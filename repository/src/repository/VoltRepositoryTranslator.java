package repository;

import java.util.*;
import static java.util.Arrays.binarySearch;
import java.io.*;
import java.lang.reflect.Method;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.*;
import org.eclipse.text.edits.*;
import org.eclipse.jface.text.*;

public class VoltRepositoryTranslator {

	public void parseFilesInDir(String path) throws IOException{
		parseFilesInDir(new File(path));
	}

	public void parseFilesInDir(File root) throws IOException{
		File[] files = root.listFiles();
		 for (File f : files) {
			 if (f.isFile() && f.getAbsolutePath().endsWith(".java")) {
				 parse(f);
			 }
		 }
	}

	private String readFileToString(File file) throws IOException {
		StringBuilder fileData = new StringBuilder(1000);
		BufferedReader reader = new BufferedReader(new FileReader(file));
 		try {
			char[] buf = new char[10];
			int numRead = 0;
			while ((numRead = reader.read(buf)) != -1) {
				String readData = String.valueOf(buf, 0, numRead);
				fileData.append(readData);
				buf = new char[1024];
			}
 		} finally {
			reader.close();
 		}
		return fileData.toString();
	}

	private void parse(File file) throws IOException {
		String str = readFileToString(file);
		ASTParser parser = ASTParser.newParser(AST.JLS3);
		Document document = new Document(str);
		parser.setSource(document.get().toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.recordModifications();
		ASTRewrite rewriter = ASTRewrite.create(cu.getAST());
		AST rast = rewriter.getAST();
		TypeDeclaration td = (TypeDeclaration) cu.types().get(0);
		boolean isRunnable = false;
		for (int i = 0; i < td.superInterfaceTypes().size(); i++) {
			Type t = (Type) td.superInterfaceTypes().get(i);
			if ("RunnableWithResult".equals(t.toString())) {
				isRunnable = true;
				break;
			};
		}
		if (isRunnable) {
			MethodDeclaration runMethod = null;
			Collection<String> sqlStatements = new ArrayList();
			for (int i = 0; i < td.getMethods().length; i++) {
				MethodDeclaration md = td.getMethods()[i];
				if ("run".equals(md.getName().toString())) {
					runMethod = md;
					parseMethod(md, imports(cu), sqlStatements, rewriter);
				}
			}
			try {
				// Rename the class
				SimpleName newName = rast.newSimpleName(td.getName().getIdentifier() + "_");
				rewriter.replace(td.getName(), newName, null);
				// Add import
				ListRewrite lrw = rewriter.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);
				ImportDeclaration id = rast.newImportDeclaration();
				id.setName(rast.newName(new String[] { "org", "voltdb" }));
				id.setOnDemand(true);
				lrw.insertLast(id, null);
				id = rast.newImportDeclaration();
				id.setName(rast.newName(new String[] { "repository", "voltdb", "CollectionFactory" }));
				id.setOnDemand(true);
				id.setStatic(true);
				lrw.insertLast(id, null);
				// Set the superclass
				rewriter.set(td, TypeDeclaration.SUPERCLASS_TYPE_PROPERTY, rast.newSimpleName("VoltProcedure"), null);
				// Remove the interfaces
				for (Object o : td.superInterfaceTypes()) {
					Type it = (Type) o;
					rewriter.remove(it, null);	
				}
				// Insert the SQL statements
				lrw = rewriter.getListRewrite(td, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
				int i = 0;
				for (String s : sqlStatements) {
					VariableDeclarationFragment vdf = rast.newVariableDeclarationFragment();
					vdf.setName(rast.newSimpleName("sql" + i));
					ClassInstanceCreation cic = rast.newClassInstanceCreation();
					cic.setType(rast.newSimpleType(rast.newSimpleName("SQLStmt")));
					StringLiteral sl = rast.newStringLiteral();
					sl.setLiteralValue(s);
					cic.arguments().add(sl);
					vdf.setInitializer(cic);
					FieldDeclaration field = rast.newFieldDeclaration(vdf);
					field.setType(rast.newSimpleType(rast.newSimpleName("SQLStmt")));
					field.modifiers().add(rast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
					field.modifiers().add(rast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));
					lrw.insertAt(field, i, null);
					i++;
				}
				// Change method signature
				rewriter.set(runMethod, MethodDeclaration.RETURN_TYPE2_PROPERTY, 
					rast.newArrayType(rast.newSimpleType(rast.newSimpleName("VoltTable")), 1), null);
				lrw = rewriter.getListRewrite(runMethod, MethodDeclaration.THROWN_EXCEPTIONS_PROPERTY);
				lrw.replace((SimpleName) runMethod.thrownExceptions().get(0), rast.newSimpleName("VoltAbortException"), null);
				lrw = rewriter.getListRewrite(runMethod, MethodDeclaration.PARAMETERS_PROPERTY);
				lrw.remove((SingleVariableDeclaration) runMethod.parameters().get(0), null);
				lrw.remove((SingleVariableDeclaration) runMethod.parameters().get(1), null);
				i = 0;
				while (runMethod.getBody().statements().get(i) instanceof VariableDeclarationStatement) {
					VariableDeclarationStatement vds = (VariableDeclarationStatement) runMethod.getBody().statements().get(i);
					VariableDeclarationFragment vdf = (VariableDeclarationFragment) vds.fragments().get(0);
					Expression e = vdf.getInitializer();
					i++;
					if (vdf.getInitializer() instanceof CastExpression) {
						e = ((CastExpression) vdf.getInitializer()).getExpression();
					}
					if (e instanceof ArrayAccess && ((ArrayAccess) e).getArray() instanceof SimpleName) {
						if (!"args".equals(((SimpleName) ((ArrayAccess) e).getArray()).getIdentifier())) {
							continue;
						}
					} else {
						if ("Repository.BatchProcessor".equals(vds.getType().toString())) {
							rewriter.remove(vds, null);
						}
						continue;
					}
					SingleVariableDeclaration svd = rast.newSingleVariableDeclaration();
					svd.setType(rast.newSimpleType(rast.newSimpleName(((SimpleType) vds.getType()).getName().getFullyQualifiedName())));
					svd.setName(rast.newSimpleName(vdf.getName().getIdentifier()));
					lrw.insertLast(svd, null);
					rewriter.remove(vds, null);
				}
				// Write down the modifications
				TextEdit edits = rewriter.rewriteAST(document, null);
				edits.apply(document);
		 		BufferedWriter out = new BufferedWriter(new FileWriter(file.getAbsolutePath().replace(".java", "_.java")));
		        try {
		            out.write(document.get());
		            out.flush();
		        } finally {
		            try {
		                out.close();
		            } catch (IOException e) {
		            }
		        }
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	public Collection<String> imports(CompilationUnit cu) {
		Collection<String> result = new LinkedHashSet<String>();
		result.add(cu.getPackage().getName().getFullyQualifiedName());
		for (int i = 0; i < cu.imports().size(); i++) {
			String qn = ((ImportDeclaration) cu.imports().get(i)).getName().getFullyQualifiedName();
			if (!qn.startsWith("java.")) {
				if (qn.endsWith(".*")) qn = qn.replace(".*", "");
				result.add(qn);
			}
		}
		return result;
	}

	private void parseMethod(MethodDeclaration md, final Collection<String> packages, 
			final Collection<String> sqlStatements, final ASTRewrite rewriter) {

		final Map<String, String> types = new LinkedHashMap<String, String>();

		for (int i = 0; i < md.parameters().size(); i++) {
			SingleVariableDeclaration svd = (SingleVariableDeclaration) md.parameters().get(i);
			types.put(svd.getName().getIdentifier(), svd.getType().toString());
		}

		md.accept(new ASTVisitor() {
			public boolean visit(VariableDeclarationStatement node) {
				VariableDeclarationStatement declaration = (VariableDeclarationStatement) node;
				for (int i = 0; i < declaration.fragments().size(); i++) {
					VariableDeclarationFragment vdf = (VariableDeclarationFragment) declaration.fragments().get(i);
					types.put(vdf.getName().getIdentifier(), declaration.getType().toString());
				}
				return false;
			}
		});

		final String[] repositoryTypes = { "Repository", "Repository.BatchProcessor" };
		final String[] sqlMethods = { "add", "matching" };

		final Block block = md.getBody();
		final int[] index = { 0 };

		block.accept(new ASTVisitor() {
			public boolean visit(MethodInvocation mi) {
				String type = "" + types.get(mi.getExpression().toString());
				if (binarySearch(repositoryTypes, type) > -1) {
					String methodName = mi.getName().getIdentifier();
					int methodIndex = binarySearch(sqlMethods, methodName);
					if (methodIndex > -1) {
						Collection<Object> arguments = new ArrayList<Object>();
						sqlStatements.add(
							builderToSql((Expression) mi.arguments().get(0), packages, sqlMethods[methodIndex], arguments));
						if ("add".equals(methodName)) {
							MethodInvocation nmi = newVoltQueueSQLInvocation(rewriter, index[0], arguments);
							if (!"Repository.BatchProcessor".equals(type)) {
								ListRewrite listRewrite = rewriter.getListRewrite(nearestParentBlock(mi), Block.STATEMENTS_PROPERTY);
								listRewrite.insertAfter(newVoltInvocation("voltExecuteSQL", rewriter.getAST()), nearestStatement(mi), null);
							}
							rewriter.replace(mi, nmi, null);
						} else if ("matching".equals(methodName)) {
							MethodInvocation nmi = newVoltQueueSQLInvocation(rewriter, index[0], arguments);
							ListRewrite listRewrite = rewriter.getListRewrite(nearestParentBlock(mi), Block.STATEMENTS_PROPERTY);
							listRewrite.insertBefore(rewriter.getAST().newExpressionStatement(nmi), nearestStatement(mi), null);
							if ("Repository.BatchProcessor".equals(type)) {
								rewriter.remove(mi, null);
							} else {
								MethodInvocation newCollectionMI = newVoltInvocation("newVoltCollection", rewriter.getAST());
								newCollectionMI.arguments().add(newVoltInvocation("voltExecuteSQL", rewriter.getAST()));
								rewriter.replace(mi, newCollectionMI, null);								
							}
						}
						index[0]++;
					} else if ("submit".equals(methodName)) {
						rewriter.replace(mi, newVoltInvocation("voltExecuteSQL", rewriter.getAST()), null);
					}
				}
				return true;
			}
		});
	}

	private MethodInvocation newVoltQueueSQLInvocation(ASTRewrite rewriter, int index, Collection<Object> arguments) {
		MethodInvocation result = newVoltInvocation("voltQueueSQL", rewriter.getAST());
		ListRewrite listRewrite = rewriter.getListRewrite(result, MethodInvocation.ARGUMENTS_PROPERTY);
		listRewrite.insertLast(rewriter.getAST().newSimpleName("sql" + index), null);
		for (Object o : arguments) {
			listRewrite.insertLast((ASTNode) o, null);
		}
		return result;
	}

	private MethodInvocation newVoltInvocation(String name, AST ast) {
		MethodInvocation result = ast.newMethodInvocation();
		result.setName(ast.newSimpleName(name));
		return result;
	}

	private Block nearestParentBlock(ASTNode node) {
		if (node == null) return null;
		if (node instanceof Block) return (Block) node;
		return nearestParentBlock(node.getParent());
	}

	private ASTNode nearestStatement(ASTNode node) {
		if (node == null || node.getParent() == null) return null;
		if (node.getParent() instanceof Block) return node;
		return nearestStatement(node.getParent());
	}

	private String builderToSql(Expression e, Collection<String> packages, String methodName, final Collection<Object> arguments) {
		final String[] ignore = { "build", "builder", "exp" };
		final String[] modifiers = { "count" };
		final Stack<String> stack = new Stack<String>();
		final String[] className = { null };
		e.accept(new ASTVisitor() {
			public boolean visit(MethodInvocation mi) {
				if ("builder".equals(mi.getName().getIdentifier())) {
					className[0] = ((SimpleName) mi.getExpression()).getIdentifier();
				}
				if (binarySearch(ignore, mi.getName().getIdentifier()) > -1) return true;
				String name = mi.getName().getIdentifier();
				if (!mi.arguments().isEmpty() && mi.arguments().get(0) instanceof NullLiteral) {
					name += "*";
				} else if (binarySearch(modifiers, name) < 0) {
					arguments.add(mi.arguments().get(0));
				}
				stack.push(name);
				return true;
			}
		});
		Map<String, Collection<Object[]>> map = new LinkedHashMap<String, Collection<Object[]>>();
		Object obj = createObject(className[0], packages);
		Object[] exp = { obj, map };
		String lastProperty = null;
		Collection<Object[]> operations = null;
		boolean lastPropertyHasNullArgument = false;
		while (!stack.isEmpty()) {
			String method = stack.pop();
			boolean hasNullArgument = false;
			if (method.endsWith("*")) {
				hasNullArgument = true;
				method = method.substring(0, method.length() - 1);
			}
			if (binarySearch(modifiers, method) > -1) {
				operations.add(new Object[] { method, null });
				if ("eq".equals(method)) {
					if (lastPropertyHasNullArgument) setPropertySomeValue(obj, lastProperty);
				}
			} else {
				if (operations != null && !operations.isEmpty()) map.put(lastProperty, operations);
				lastPropertyHasNullArgument = hasNullArgument;
				if (!hasNullArgument) setPropertySomeValue(obj, method);
				lastProperty = method;
				operations = new ArrayList<Object[]>();
			}
		}
		if (operations != null && !operations.isEmpty()) map.put(lastProperty, operations);
		try {
			SqlRepository repository = new SqlRepository();
			repository.setUsingQuestionMarks(true);
			if ("matching".equals(methodName)) {
				return repository.queryString(exp);
			} else {
				return repository.insertString(exp);
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private Object createObject(String className, Collection<String> packages) {
		for (String p : packages) {
			if (p.endsWith("." + className)) {
				return createObject(p);
			}
		}
		for (String p : packages) {
			try {
				return Class.forName(p + "." + className).newInstance();
			} catch (Exception ex) {
			}
		}
		throw new RuntimeException(String.format("A classe %s não foi encontrada em nenhum dos pacotes: %s", className, packages));
	}

	private Object createObject(String qualifiedClassName) {
		try {
			return Class.forName(qualifiedClassName).newInstance();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void setPropertySomeValue(Object obj, String propertyName) {
		try {
			for (Method m : obj.getClass().getDeclaredMethods()) {
				if (m.getName().toLowerCase().equals("set" + propertyName.toLowerCase())) {
					if (m.getParameterTypes()[0].isAssignableFrom(Integer.class)) {
						m.invoke(obj, 0);
					} else if (m.getParameterTypes()[0].isAssignableFrom(Long.class)) {
						m.invoke(obj, 0l);
					} else if (m.getParameterTypes()[0].isAssignableFrom(String.class)) {
						m.invoke(obj, "");
					} else if (m.getParameterTypes()[0].isAssignableFrom(Date.class)) {
						m.invoke(obj, new Date());
					}
				}
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

}
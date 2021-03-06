package repository.fluentBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class BuilderGenerator implements Generator {

	public void generate(ICompilationUnit cu, boolean createBuilderConstructor, boolean formatSource, 
			List<IField> fields) {
		try {
			IBuffer buffer = cu.getBuffer();
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println();
			createClassDeclaration(pw);
			pw.println("{");

			IType clazz = cu.getTypes()[0];

			int pos = clazz.getSourceRange().getOffset() + clazz.getSourceRange().getLength() - 1;

			createFieldDeclarations(pw, clazz, fields);

			createBuilderMethods(pw, clazz, fields);
			if (createBuilderConstructor) {
				createPrivateBuilderConstructor(pw, clazz, fields);
				pw.println("}");
			} else {
				createClassBuilderConstructor(pw, clazz, fields);
				pw.println("}");
				createClassConstructor(pw, clazz, fields);
			}

			if (formatSource) {
				buffer.replace(pos, 0, sw.toString());
				String builderSource = buffer.getContents();

				TextEdit text = ToolFactory.createCodeFormatter(null).format(CodeFormatter.K_COMPILATION_UNIT, 
					builderSource, 0, builderSource.length(), 0, "\n");
				// text is null if source cannot be formatted
				if (text != null) {
					Document simpleDocument = new Document(builderSource);
					text.apply(simpleDocument);
					buffer.setContents(simpleDocument.get());
				}
			} else {
				buffer.replace(pos, 0, sw.toString());
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			e.printStackTrace();
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	protected void createClassDeclaration(PrintWriter pw) {
		pw.println("public static class Builder");
	}

	protected void createClassConstructor(PrintWriter pw, IType clazz, List<IField> fields) throws JavaModelException {
		String clazzName = clazz.getElementName();
		pw.println("private " + clazzName + "(Builder builder){");
		for (IField field : fields) {
			pw.println("this." + getName(field) + "=builder." + getName(field) + ";");
		}
		pw.println("}");
	}

	protected void createClassBuilderConstructor(PrintWriter pw, IType clazz, List<IField> fields) {
		String clazzName = clazz.getElementName();
		pw.println("public " + clazzName + " build(){");
		pw.println("return new " + clazzName + "(this);\n}");
	}

	protected void createPrivateBuilderConstructor(PrintWriter pw, IType clazz, List<IField> fields) {
		String clazzName = clazz.getElementName();
		String clazzVariable = clazzName.substring(0, 1).toLowerCase() + clazzName.substring(1);
		pw.println("public " + clazzName + " build(){");
		pw.println(clazzName + " " + clazzVariable + "=new " + clazzName + "();");
		for (IField field : fields) {
			String name = getName(field);
			pw.println(clazzVariable + "." + name + "=" + name + ";");
		}
		pw.println("return " + clazzVariable + ";\n}");
	}

	protected void createBuilderMethods(PrintWriter pw, IType clazz, List<IField> fields) throws JavaModelException {
		for (IField field : fields) {
			String name = getName(field);
			String type = getType(field);
			pw.println("public Builder " + name + "(" + type + " " + name + ") {");
			pw.println("this." + name + "=" + name + ";");
			pw.println("return this;\n}");
		}
	}

	protected void createFieldDeclarations(PrintWriter pw, IType clazz, List<IField> fields) throws JavaModelException {
		for (IField field : fields) {
			pw.println("private " + getType(field) + " " + getName(field) + ";");
		}
	}

	public String getName(IField field) {
		return field.getElementName();
	}

	public String getType(IField field) {
		try {
			return Signature.toString(field.getTypeSignature());
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return null;
	}

	public List<IField> findAllFIelds(ICompilationUnit compilationUnit) {
		List<IField> fields = new ArrayList<IField>();
		try {
			IType clazz = compilationUnit.getTypes()[0];

			for (IField field : clazz.getFields()) {
				int flags = field.getFlags();
				boolean notFinal = !Flags.isFinal(flags);
				boolean notStatic = !Flags.isStatic(flags);
				if (notFinal && notStatic) {
					fields.add(field);
				}
			}

		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return fields;
	}

}

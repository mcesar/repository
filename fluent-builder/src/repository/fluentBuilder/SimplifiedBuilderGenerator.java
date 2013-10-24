package repository.fluentBuilder;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

public class SimplifiedBuilderGenerator extends BuilderGenerator {

	private static Collection<String> COMMON_TYPES = Arrays.asList("String", "Date", "Timestamp", "Integer", "Long", 
		"List", "Collection", "Set", "Map", "Double", "Boolean");

	protected void createClassDeclaration(PrintWriter pw) {
		pw.println("public static class Builder<T extends Builder<?>> extends AbstractBuilder<T>");
	};

	@Override
	protected void createClassConstructor(PrintWriter pw, IType clazz, List<IField> fields) throws JavaModelException {
		pw.println("public static Builder<Builder<?>> builder() {");
		pw.println("	return new Builder<Builder<?>>();");
		pw.println("}");
		Set<String> processed = new LinkedHashSet<String>();
		for (IField field : fields) {
			String s = typeSignature(field);
			if (processed.contains(s)) continue;
			processed.add(s);
			if (field.getTypeSignature().charAt(0) == 'Q' && !COMMON_TYPES.contains(s)) {
				pw.println(String.format("public static class %sBuilder extends %s.Builder<%sBuilder> {", s, s, s));
				pw.println("private Builder<?> b;");
				pw.println(String.format("public %sBuilder(Builder<?> b) {", s));
				pw.println("this.b = b;\n}");
				pw.println("public Builder<?> end() {");
				pw.println(String.format("b.addInnerNonDefaultOperations(\"%s\", exp()[1]);", field.getElementName()));
				pw.println("return b;\n}");
				pw.println(String.format("protected %sBuilder add(String prefix, Object[] exp) {", s));
				pw.println(String.format("b.addInnerNonDefaultOperations(\"%s.\" + prefix, exp[1]);", 
					field.getElementName()));
				pw.println("return this;\n}\n}");
			}
		}
	}

	@Override
	protected void createClassBuilderConstructor(PrintWriter pw, IType clazz, List<IField> fields) {
		String clazzName = clazz.getElementName();
		pw.println("public " + clazzName + " build(){");
		pw.println("return obj;\n}");
	}

	@Override
	protected void createFieldDeclarations(PrintWriter pw, IType clazz, List<IField> fields) throws JavaModelException {
		String className = clazz.getElementName();
		pw.println(String.format("private %s obj = new %s();", className, className));
		pw.println("public Builder() {");
		pw.println("RepositoryUtil.nulifyProperties(obj);");
		pw.println("}");
	}

	protected void createBuilderMethods(PrintWriter pw, IType clazz, List<IField> fields) throws JavaModelException {
		for (IField field : fields) {
			String name = getName(field);
			String type = getType(field);
			pw.println("@SuppressWarnings(\"unchecked\")");
			pw.println(String.format("public T %s(%s %s) {", name, type, name));
			pw.println(String.format("obj.%s=%s;", name, name));
			pw.println(String.format("lastProperty=\"%s\";", name));
			pw.println(String.format("lastValue=%s;", name));
			pw.println("return (T) this;\n}");
			String s = typeSignature(field);
			if (field.getTypeSignature().charAt(0) == 'Q' && !COMMON_TYPES.contains(s)) {
				pw.println(String.format("public %s.Builder<%sBuilder> %s() {", s, s, name));
				pw.println(String.format("%s.Builder<%sBuilder> result = new %sBuilder(this);", s, s, s));
				pw.println(String.format("obj.%s = result.build();", name));
				pw.println(String.format("return result;\n}", s));
				pw.println("@SuppressWarnings(\"unchecked\")");
				pw.println(String.format("public T %s(Object[] exp) {", name));
				pw.println(String.format("obj.%s = (%s) exp[0];", name, type));
				pw.println(String.format("add(\"%s\", exp);", name));
				pw.println("return (T) this;\n}");
			}
		}
	}

	private String typeSignature(IField field) throws JavaModelException {
		String s = Signature.toString(field.getTypeSignature());
		if (s.indexOf('<') > 0) s = s.substring(0, s.indexOf('<'));
		return s;
	}
}
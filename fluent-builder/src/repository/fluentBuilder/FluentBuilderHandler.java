package repository.fluentBuilder;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;

public class FluentBuilderHandler extends AbstractHandler {


	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkingCopyManager manager = JavaUI.getWorkingCopyManager();
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		IEditorInput editorInput = editor.getEditorInput();
		try {
			manager.connect(editorInput);
			ICompilationUnit workingCopy = manager.getWorkingCopy(editorInput);

			CreateDialog dialog = new CreateDialog(new Shell(), new SimplifiedBuilderGenerator());
			dialog.show(workingCopy);

			synchronized (workingCopy) {
				workingCopy.reconcile(ICompilationUnit.NO_AST, false, null, null);
			}

		} catch (JavaModelException e) {
			e.printStackTrace();
		} catch (CoreException e) {
			e.printStackTrace();
		} finally {
			manager.disconnect(editorInput);
		}
		return null;
	}
}

package jp.try0.soc;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.junit.jupiter.api.Test;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionMethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import com.google.common.collect.Lists;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

/**
 * TODO optionalてきとー
 * 
 * @author
 *
 */
public class Example {

	/**
	 * 呼び出し階層ノード
	 * 
	 * @author
	 *
	 */
	class CallNode {

		String signature;

		MethodDeclaration d;

		public CallNode(String signature, MethodDeclaration d) {
			this.signature = signature;
			this.d = d;
		}

		@Override
		public int hashCode() {
			return Objects.hash(signature);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof CallNode)) {
				return false;
			}
			var c = (CallNode) obj;
			return signature.equals(c.signature);
		}
	}

	private static PackageDeclaration findPackage(MethodDeclaration md) {
		var cu = md.findCompilationUnit().get();
		return cu.getPackageDeclaration().get();
	}

	private static TypeDeclaration<TypeDeclaration<?>> findType(Optional<Node> node) {
		while (node.isPresent()) {
			if (node.get() instanceof TypeDeclaration) {
				break;
			}

			if (node.get() instanceof ObjectCreationExpr) {
				ObjectCreationExpr newExpr = (ObjectCreationExpr) node.get();
				if (newExpr.getAnonymousClassBody().isPresent()) {

					try {
						node = newExpr.getType().resolve().asReferenceType().getTypeDeclaration().get().toAst();

						if (node.isPresent()) {

							break;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			if (node.isEmpty()) {
				return null;
			}

			node = node.get().getParentNode();
		}

		if (node.isEmpty()) {
			return null;
		}

		TypeDeclaration<TypeDeclaration<?>> td = (TypeDeclaration<TypeDeclaration<?>>) node.get();

		return td;
	}

	class MethodCallVisitor extends VoidVisitorAdapter<Void> {
		@Override
		public void visit(MethodCallExpr n, Void arg) {

			Optional<Node> node = Optional.of(n);
			while (node.isPresent()) {
				if (node.get() instanceof MethodDeclaration) {
					break;
				}
				node = node.get().getParentNode();
			}

			MethodDeclaration md = (MethodDeclaration) node.get();
			PackageDeclaration pkg = findPackage(md);
			TypeDeclaration<?> td = Example.findType(node);

			if (td == null) {
				System.out.println(" <-- Unresolved " + md.getSignature());
			} else {
				var sig = pkg.getName() + "." + td.getName() + "#" + md.getSignature();
				System.out.println(sig);

				try {

					ResolvedMethodDeclaration ref = n.resolve();

					if (ref instanceof ReflectionMethodDeclaration) {

						ReflectionMethodDeclaration rmd = (ReflectionMethodDeclaration) ref;
						var sigResolve = rmd.getPackageName() + "." + rmd.getClassName() + "#" + rmd.getSignature();
						System.out.println(" <-- " + sigResolve);

						callGraph.putEdgeValue(new CallNode(sigResolve, null), new CallNode(sig, md), "");
					} else if (ref instanceof JavaParserMethodDeclaration) {

						MethodDeclaration mdRef = (MethodDeclaration) ref.toAst().get();
						PackageDeclaration pkgRef = findPackage(md);
						TypeDeclaration<?> tdRef = Example.findType(ref.toAst());

						var sigResolve = pkgRef.getName() + "." + tdRef.getName() + "#" + mdRef.getSignature();
						System.out.println(" <-- " + sigResolve);

						callGraph.putEdgeValue(new CallNode(sigResolve, mdRef), new CallNode(sig, md), "");
					}
				} catch (Exception e) {
					System.out.println(" <-- Unresolved " + n);
				} catch (NoClassDefFoundError e) {
					System.out.println(" <-- Unresolved " + n);
				}
			}

//			md.getJavadoc().ifPresent(jdc -> {
//
//				var tags = jdc.getBlockTags();
//
//				for (var tag : tags) {
//
//					System.out.println(tag.getTagName() + " " + tag.getContent().toText());
//				}
//			});

			super.visit(n, arg);
		}
	}

	static MutableValueGraph<CallNode, String> callGraph;
	static TypeSolver typeResolver;

	@Test
	public void run() throws Exception {
		callGraph = ValueGraphBuilder.directed().allowsSelfLoops(false).build();

//		Map<String, String> env = System.getenv();
//		typeResolver = new CombinedTypeSolver(new ClassLoaderTypeSolver(ClassLoader.getSystemClassLoader()),
//				new ReflectionTypeSolver(),
//				new JavaParserTypeSolver(
//						new File(gitRepDir() + "\\wicket-iziToast\\wicket-izitoast-core\\src\\main\\java")),
//				new JavaParserTypeSolver(
//						new File(gitRepDir() + "\\wicket-iziToast\\wicket-izitoast-samples\\src\\main\\java")),
//				new JarTypeSolver(env.get("USERPROFILE")
//						+ "\\.m2\\repository\\org\\apache\\wicket\\wicket-core\\9.12.0\\wicket-core-9.12.0.jar"));
//		ParserConfiguration config = new ParserConfiguration();
//		config.setSymbolResolver(new JavaSymbolSolver(typeResolver));
//		StaticJavaParser.setConfiguration(config);
//
//		List<CompilationUnit> cus = Lists.newArrayList();
//
//		for (var pjDir : Arrays.asList(
//				new File(gitRepDir() + "\\wicket-iziToast\\wicket-izitoast-core\\src\\main\\java"),
//				new File(gitRepDir() + "\\wicket-iziToast\\wicket-izitoast-samples\\src\\main\\java"))) {
//			SourceRoot sourceRoot = new SourceRoot(pjDir.toPath());
//			sourceRoot.setParserConfiguration(config);
//			sourceRoot.tryToParse();
//
//			Iterator<CompilationUnit> it = sourceRoot.getCompilationUnits().iterator();
//			while (it.hasNext()) {
//
//				CompilationUnit cu = it.next();
//				cus.add(cu);
//			}
//		}

		Map<String, String> env = System.getenv();
		typeResolver = new CombinedTypeSolver(new ClassLoaderTypeSolver(ClassLoader.getSystemClassLoader()),
				new ReflectionTypeSolver(),
				new JavaParserTypeSolver(new File(gitRepDir() + "\\java-scope-of-change\\src\\main\\java")));
		ParserConfiguration config = new ParserConfiguration();
		config.setSymbolResolver(new JavaSymbolSolver(typeResolver));
		StaticJavaParser.setConfiguration(config);

		List<CompilationUnit> cus = Lists.newArrayList();

		for (var pjDir : Arrays.asList(new File(gitRepDir() + "\\java-scope-of-change\\src\\main\\java"))) {
			SourceRoot sourceRoot = new SourceRoot(pjDir.toPath());
			sourceRoot.setParserConfiguration(config);
			sourceRoot.tryToParse();

			Iterator<CompilationUnit> it = sourceRoot.getCompilationUnits().iterator();
			while (it.hasNext()) {

				CompilationUnit cu = it.next();
				cus.add(cu);
			}
		}

		Iterator<CompilationUnit> it = cus.iterator();
		while (it.hasNext()) {
			CompilationUnit cu = it.next();

			for (var type : cu.findAll(TypeDeclaration.class)) {

				for (MethodDeclaration method : type.findAll(MethodDeclaration.class)) {
					System.out.println(type.getNameAsString() + "#" + method.getSignature().asString());

					method.accept(new MethodCallVisitor(), null);
					System.out.println();
				}
			}
		}

		if (true) {
			return;
		}

		System.out.println(callGraph);

		System.out.println();

		it = cus.iterator();
		while (it.hasNext()) {
			CompilationUnit cu = it.next();

			for (var type : cu.findAll(TypeDeclaration.class)) {

				for (MethodDeclaration method : type.findAll(MethodDeclaration.class)) {
					var sig = cu.getPackageDeclaration().get().getName() + "." + type.getNameAsString() + "#"
							+ method.getSignature().asString();

					printGraph(new CallNode(sig, null), 0);
				}
			}
		}

		Repository repository = new FileRepository(gitRepDir() + "\\wicket-iziToast\\.git");
		Git git = new Git(repository);

		Iterable<RevCommit> log = git.log().call();
		for (var c : log) {
			System.out.println(c.getId().getName() + " " + c.getShortMessage());
		}

		System.out.println();
		System.out.println();

		if (true) {
			return;
		}

//		ObjectId oldHead = repository.resolve("HEAD^^^^{tree}");
//		ObjectId head = repository.resolve("HEAD^{tree}");

		ObjectId oldHead = repository.resolve("c9d58ae327616030f1f08a299ae1dd899428ea6c^{tree}");
		ObjectId head = repository.resolve("HEAD^{tree}");

		ObjectReader reader = repository.newObjectReader();

		CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
		oldTreeParser.reset(reader, oldHead);
		CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
		newTreeParser.reset(reader, head);

		DiffFormatter formatter = new DiffFormatter(System.out);
		formatter.setRepository(repository);
		formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
		List<DiffEntry> diffs = git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).call();
		for (DiffEntry entry : diffs) {
			System.out.println("■ old: " + entry.getOldPath() + ", new: " + entry.getNewPath() + ", entry: " + entry);

//			formatter.format(entry);
			boolean hit = false;
			var fileHeader = formatter.toFileHeader(entry);
			var editList = fileHeader.toEditList();

			if (entry.getNewPath().endsWith(".java")) {
				it = cus.iterator();
				while (it.hasNext()) {
					CompilationUnit cu = it.next();

					var strage = cu.getStorage().get();
					var fileName = strage.getPath().toString().replace(strage.getSourceRoot().toString(), "")
							.replace("\\", "/");

					if (entry.getNewPath().endsWith(fileName.toString())) {

						for (var type : cu.findAll(TypeDeclaration.class)) {

							for (MethodDeclaration method : type.findAll(MethodDeclaration.class)) {
								var sig = cu.getPackageDeclaration().get().getName() + "." + type.getNameAsString()
										+ "#" + method.getSignature().asString();

								if (entry.getChangeType() == ChangeType.MODIFY) {
									var start = method.getBegin().get().line;
									var end = method.getEnd().get().line;

									for (var edit : editList) {

										if (edit.getBeginB() >= start && edit.getBeginB() <= end) {
											System.out.println("EditMethod:\n" + sig);
											printGraph(new CallNode(sig, null), 0);
											hit = true;
											break;
										}
									}
								} else if (entry.getChangeType() == ChangeType.ADD) {
									printGraph(new CallNode(sig, null), 0);
//									hit = true;
								}
							}

						}
					}

				}
			}

			if (hit) {
				formatter.format(entry);
			}
		}
	}

	private String gitRepDir() {
		Map<String, String> env = System.getenv();
		return env.get("USERPROFILE") + "\\git";
	}

	private void printGraph(CallNode n, final int depth) {
		if (callGraph.nodes().contains(n)) {
			System.out.println(indent(depth) + n.signature);
			if (n.d != null) {
//			n.d.getJavadoc().ifPresent(jdc -> {
//				var tags = jdc.getBlockTags();
//				for (var tag : tags) {
//					System.out.println(indent(depth) + tag.getTagName() + " " + tag.getContent().toText());
//				}
//			});
			}

			callGraph.successors(n).forEach(cNode -> {
				printGraph(cNode, depth + 1);
			});
		}
	}

	private String indent(int depth) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			sb.append(" ");
		}
		return sb.toString();
	}

}

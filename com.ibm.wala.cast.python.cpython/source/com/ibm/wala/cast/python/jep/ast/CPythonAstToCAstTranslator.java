package com.ibm.wala.cast.python.jep.ast;

import static com.ibm.wala.cast.python.jep.Util.interps;
import static com.ibm.wala.cast.python.jep.Util.runit;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.cast.python.loader.JepPythonLoaderFactory;
import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.ir.translator.AbstractScriptEntity;
import com.ibm.wala.cast.ir.translator.TranslatorToCAst;
import com.ibm.wala.cast.python.ir.PythonCAstToIRTranslator;
import com.ibm.wala.cast.python.ir.PythonLanguage;
import com.ibm.wala.cast.python.loader.PythonLoaderFactory;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstQualifier;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.tree.CAstType;
import com.ibm.wala.cast.tree.impl.CAstControlFlowRecorder;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstOperator;
import com.ibm.wala.cast.tree.impl.CAstSourcePositionRecorder;
import com.ibm.wala.cast.tree.impl.CAstSymbolImpl;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.CopyKey;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.RewriteContext;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.cast.tree.visit.CAstVisitor.Context;
import com.ibm.wala.cast.util.CAstPattern;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.classLoader.SourceModule;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.cha.SeqClassHierarchyFactory;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.collections.HashSetFactory;

import jep.Interpreter;
import jep.python.PyObject;

/**
 * An api for creating a WALA CASst representation of the standard Python
 * ASTs given source code.
 */
public class CPythonAstToCAstTranslator implements TranslatorToCAst {

	  private static CAstType codeBody =
		      new CAstType() {

		        @Override
		        public String getName() {
		          return "CodeBody";
		        }

		        @Override
		        public Collection<CAstType> getSupertypes() {
		          return Collections.emptySet();
		        }
		      };

	
	/**
	 * parse Python code into the standard CPython AST
	 * @param code source code to be parsed into an AST
	 * @return AST as a @PyObject
	 */
	public static PyObject getAST(String code) {
		Interpreter interp = interps.get();
		
		interp.set("code", code);
		interp.exec("theast = ast.parse(code)");
		
		return (PyObject) interp.getValue("theast");
	}
	
	/**
	 * turn an AST into a JSON representation
	 * @param ast a Python AST as a @PyObject
	 * @return JSON form of the AST as tree of @Map objects
	 */
	@SuppressWarnings("unchecked")
	public static Map<String,?> getJSON(PyObject ast) {
		Interpreter interp = interps.get();

		interp.set("theast", ast);
		interp.exec("thejson = ast2json.ast2json(theast)");
		
		return (Map<String,?>) interp.getValue("thejson");
		
	}
	
	@SuppressWarnings("unchecked")
	public static Collection<String> properties(PyObject obj) {
		Interpreter interp = interps.get();

		interp.set("obj", obj);
		interp.exec("d = dir(obj)");
		
		return (Collection<String>) interp.getValue("d");
		
	}

	private static final CAstPattern nm = CAstPattern.parse("ASSIGN(VAR(<n>*),**)");

	public static  final class PythonScriptEntity extends AbstractCodeEntity {
		private final String fn;

		private static CAstType makeType(String fn) {
	   return
	           new CAstType.Function() {
				
           @Override
           public Collection<CAstType> getSupertypes() {
             return Collections.singleton(codeBody);
           }
	             @Override
	             public String getName() {
	               return fn;
	             }

	             @Override
				public CAstType getReturnType() {
					// TODO Auto-generated method stub
					return null;
				}
				
				@Override
				public Collection<CAstType> getExceptionTypes() {
					// TODO Auto-generated method stub
					return null;
				}
				
				@Override
				public List<CAstType> getArgumentTypes() {
					// TODO Auto-generated method stub
					return null;
				}
				
				@Override
				public int getArgumentCount() {
					return 1;
				}
			};
		}
		
		   private PythonScriptEntity(String fn, Path pn) throws IOException {
				this(makeType(fn), fn, Files.readString(pn));
	   }
	
		   public PythonScriptEntity(String fn, String code) throws IOException {
				this(makeType(fn), fn, code);
	   }
			

		public PythonScriptEntity(CAstType type, String fn, String scriptCode) throws IOException {
			super(type);
			this.fn = fn;
				PyObject ast = CPythonAstToCAstTranslator.getAST(scriptCode);

				TestVisitor x = new TestVisitor(this) {
					@Override
					public URL url() {
						try {
							return new URL("file:" + fn);
						} catch (MalformedURLException e) {
							return null;
						}
					}
				};
				
				Ast = x.visit(ast, new ScriptContext(ast, new CAstImpl(), this));
			}

		@Override
		public int getKind() {
			return CAstEntity.SCRIPT_ENTITY;
		}

		@Override
		public String getName() {
			return fn;
		}

		
		@Override
		public String getSignature() {
			return "script " + getName();
		}

		@Override
		public String[] getArgumentNames() {
			return new String[] { "self" };
		}

		@Override
		public CAstNode[] getArgumentDefaults() {
			return new CAstNode[0];
		}

		@Override
		public int getArgumentCount() {
			return 0;
		}

		@Override
		public Position getNamePosition() {
			return null;
		}

		@Override
		public Position getPosition(int arg) {
			return null;
		}

		@Override
		public Collection<CAstQualifier> getQualifiers() {
			return null;
		}
	}

	public interface WalkContext extends TranslatorToCAst.WalkContext<WalkContext, PyObject> {
		Scope scope();
	}

	public static class ScriptContext extends TranslatorToCAst.FunctionContext<WalkContext, PyObject>
			implements WalkContext {
		private final Scope scope; 
		
		private final PyObject ast;
		PythonScriptEntity self;
		
		protected ScriptContext(PyObject s, CAstImpl ast, PythonScriptEntity self) {
			super(null, s);
			this.ast = s;
			this.self = self;
			scope = new Scope() {
				@Override
				Scope parent() {
					return null;
				} 				
			};
		}
		
		public Scope scope() {
			return scope;
		}
		
		@Override
		public PyObject top() {
			return ast;
		}

		@Override
		public WalkContext getParent() {
			assert false;
			return null;
		}

		@Override
		public CAstNode getCatchTarget() {
			assert false;
			return null;
		}

		@Override
		public CAstNode getCatchTarget(String s) {
			assert false;
			return null;
		}
		
		@Override
		public CAstControlFlowRecorder cfg() {
			return (CAstControlFlowRecorder) self.getControlFlow();
		}

		@Override
		public CAstSourcePositionRecorder pos() {
			return (CAstSourcePositionRecorder) self.getSourceMap();
		}

		@Override
		public void addScopedEntity(CAstNode construct, CAstEntity e) {
			self.addScopedEntity(construct, e);
		}

		@Override
		public Map<CAstNode, Collection<CAstEntity>> getScopedEntities() {
			return self.getAllScopedEntities();
		}
	}

	private static abstract class Scope {
		abstract Scope parent();
		
		Set<String> nonLocalNames = HashSetFactory.make();
		Set<String> globalNames = HashSetFactory.make();
		Set<String> allNames = HashSetFactory.make();
		Set<String> writtenNames = HashSetFactory.make();
	}
	
	private static class FunctionContext extends TranslatorToCAst.FunctionContext<WalkContext, PyObject>
		implements WalkContext {
		private final Scope scope; 
		
		public Scope scope() {
			return scope;
		}

		protected FunctionContext(WalkContext parent, PyObject s) {
			super(parent, s);
			scope = new Scope() {

				@Override
				Scope parent() {
					return parent.scope();
				}
				
			};
		}
	}

	private static class LoopContext extends TranslatorToCAst.LoopContext<WalkContext, PyObject>
			implements WalkContext {

		LoopContext(WalkContext parent, PyObject breakTo, PyObject continueTo) {
			super(parent, breakTo, continueTo, null);
		}

		private boolean continued = false;
		private boolean broke = false;
				
		@Override
		public Scope scope() {
			return parent.scope();
		}

		@Override
		public PyObject getContinueFor(String l) {
			continued = true;
			return super.getContinueFor(l);
		}


		@Override
		public PyObject getBreakFor(String l) {
			broke = true;
			return super.getBreakFor(l);
		}


		@Override
		public WalkContext getParent() {
			return (WalkContext) super.getParent();
		}
	}

	public static abstract class TestVisitor implements JepAstVisitor<CAstNode, WalkContext> {
		CAst ast = new CAstImpl();
		private int label = 0;
		private final CAstEntity entity;
		private int tmpIndex = 0;
		
		private TestVisitor(CAstEntity self) {
			entity = self;
		}
		
		@Override
		public CAstNode visit(PyObject o, WalkContext context) {
			Position pos = pos(o);
			CAstNode n = JepAstVisitor.super.visit(o, context);
			if (pos != null) {
				context.pos().setPosition(n, pos);
			}
			return n;
		}

		@Override
		public CAstNode visitModule(PyObject o, WalkContext context) {
			@SuppressWarnings("unchecked")
			List<PyObject> body = (List<PyObject>) o.getAttr("body");
			CAstNode bodyAst = ast.makeNode(CAstNode.BLOCK_STMT, body.stream().map(f -> visit(f, context)).collect(Collectors.toList()));
			
			Set<String> exposedNames = exposedNames(bodyAst);
			if (exposedNames.size() > 0)
				return ast.makeNode(CAstNode.UNWIND,
						bodyAst,
						ast.makeNode(CAstNode.BLOCK_STMT,
								exposedNames.stream()
								.map(n -> ast.makeNode(CAstNode.ASSIGN, 
										ast.makeNode(CAstNode.OBJECT_REF, ast.makeNode(CAstNode.THIS), ast.makeConstant(n)),
										ast.makeNode(CAstNode.VAR, ast.makeConstant(n))))
								.collect(Collectors.toList())));
			else
				return bodyAst;
		}		
		
		@SuppressWarnings("unchecked")
		public CAstNode visitFunctionDef(PyObject o, WalkContext context) {
			WalkContext fc = new FunctionContext(context, o);
			
			String functionName = (String) o.getAttr("name");
			
			CAstNode body = 
					ast.makeNode(CAstNode.LOCAL_SCOPE,
				visit(CAstNode.BLOCK_STMT, 
					  ((List<PyObject>)o.getAttr("body")).stream().collect(Collectors.toList()),
			          fc));
			
			Object rawArgs = o.getAttr("args");
			List<PyObject> arguments;
			if (rawArgs instanceof List) {
				arguments = (List<PyObject>) rawArgs;
			} else if (rawArgs instanceof PyObject) {
				arguments = Arrays.asList((PyObject)rawArgs);
			} else {
				arguments = null;
				assert false;
			}
			
			int argumentCount = arguments.size();

			List<CAstType> argumentTypes = Collections.nCopies(argumentCount, CAstType.DYNAMIC);
			
			CAstType.Function funType = new CAstType.Function() {
				
				@Override
				public Collection<CAstType> getSupertypes() {
					return Collections.singleton(codeBody);
				}
				
				@Override
				public String getName() {
					return functionName;
				}
				
				@Override
				public CAstType getReturnType() {
					return CAstType.DYNAMIC;
				}
				
				@Override
				public Collection<CAstType> getExceptionTypes() {
					return Collections.emptyList();
				}
				
				@Override
				public List<CAstType> getArgumentTypes() {
					return argumentTypes;
				}
				
				@Override
				public int getArgumentCount() {
					return argumentCount;
				}
			}; 
			
			CAstEntity fun = new AbstractScriptEntity(functionName, funType) {

				@Override
				public int getKind() {
					return CAstEntity.FUNCTION_ENTITY;
				}

				@Override
				public String getName() {
					return functionName;
				}

				@Override
				public String getSignature() {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public String[] getArgumentNames() {
					List<String> names = arguments.stream().map(a -> a.toString()).collect(Collectors.toList());
					return names.toArray(new String[names.size()]);
				}

				@Override
				public CAstNode[] getArgumentDefaults() {
					return new CAstNode[0];
				}

				@Override
				public int getArgumentCount() {
					return argumentCount;
				}

				@Override
				public Map<CAstNode, Collection<CAstEntity>> getAllScopedEntities() {
					return fc.getScopedEntities();
				}

				@Override
				public Iterator<CAstEntity> getScopedEntities(CAstNode construct) {
					if (fc.getScopedEntities().containsKey(construct)) {
						return fc.getScopedEntities().get(construct).iterator();
					} else {
						return EmptyIterator.instance();
					}
				}

				@Override
				public CAstNode getAST() {
					return body;
				}

				@Override
				public Position getNamePosition() {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public Position getPosition(int arg) {
					// TODO Auto-generated method stub
					return null;
				}
			};
			
			CAstNode fe = ast.makeNode(CAstNode.FUNCTION_EXPR, ast.makeConstant(fun));
			context.addScopedEntity(fe, fun);
			CAstNode node = ast.makeNode(CAstNode.DECL_STMT,
					ast.makeConstant(new CAstSymbolImpl(functionName, funType)),	
					fe);
			
			return node;
		}

		public CAstNode visitName(PyObject o, WalkContext context) {
			String nm = (String) o.getAttr("id");

			context.scope().allNames.add(nm);
			 
			return ast.makeNode(CAstNode.VAR, ast.makeConstant(nm));
		}

		public CAstNode visitConstant(PyObject o, WalkContext context) {
			Object nm = o.getAttr("value");
			return ast.makeConstant(nm);
		}

		public CAstNode visitAssign(PyObject o, WalkContext context) {
			PyObject value = (PyObject) o.getAttr("value");
			CAstNode rhs = visit(value, context);
			@SuppressWarnings("unchecked")
			List<PyObject> body = (List<PyObject>) o.getAttr("targets");
			body.forEach(e -> {
				CAstNode node = visit(e, context);
				if (node.getKind() == CAstNode.VAR) {
					String varName = (String) node.getChild(0).getValue();
					context.scope().writtenNames.add(varName);
				}
			});
			return ast.makeNode(CAstNode.BLOCK_STMT, body.stream()
					.map(f -> ast.makeNode(CAstNode.ASSIGN, visit(f, context), rhs))
					.collect(Collectors.toList()));

		}
		
		private CAstNode visit(int node, List<PyObject> l, WalkContext context) {
			return ast.makeNode(node, 
			  l.stream()
			    .map(f -> visit(f, context))
			    .collect(Collectors.toList()));
		}
		
		public CAstNode visitCall(PyObject o, WalkContext context) {
			PyObject func = o.getAttr("func", PyObject.class);
			@SuppressWarnings("unchecked")
			List<PyObject> args = (List<PyObject>) o.getAttr("args");
			@SuppressWarnings("unchecked")
			List<PyObject> keywords = (List<PyObject>) o.getAttr("keywords");
			
			List<CAstNode> ak = new ArrayList<>();
			ak.add(visit(func, context));
		    ak.add(ast.makeNode(CAstNode.EMPTY));

			args.forEach(a -> { 
				ak.add(visit(a, context));
			});
			for(PyObject k : keywords) {
				ak.add(ast.makeNode(
						CAstNode.ARRAY_LITERAL,
						ast.makeConstant(k.getAttr("arg", String.class)),
						visit(k.getAttr("value", PyObject.class), context)));
			}
			
			return ast.makeNode(CAstNode.CALL, ak);
					
		}

		public CAstNode visitImportFrom(PyObject o, WalkContext context) {
			String module = (String) o.getAttr("module");
			@SuppressWarnings("unchecked")
			List<PyObject> alias = (List<PyObject>) o.getAttr("names");
			return ast.makeNode(CAstNode.BLOCK_STMT, 
			alias.stream().map(a -> ast.makeNode(CAstNode.ASSIGN, 
					ast.makeNode(CAstNode.VAR, ast.makeConstant(a.getAttr("name"))),
					ast.makeNode(CAstNode.OBJECT_REF, 
						ast.makeNode(CAstNode.VAR, ast.makeConstant(module)),
						ast.makeConstant(a.getAttr("name"))))).collect(Collectors.toList()));
		}

		public CAstNode visitExpr(PyObject o, WalkContext context) {
			return visit(o.getAttr("value", PyObject.class), context);
		}

		public CAstNode visitBoolOp(PyObject o, WalkContext context) {
			PyObject op = (PyObject) o.getAttr("op");
			String opName = op.getAttr("__class__", PyObject.class).getAttr("__name__", String.class);

			@SuppressWarnings("unchecked")
			List<PyObject> values = (List<PyObject>) o.getAttr("values");
			CAstNode result = visit(values.get(0), context);

			for (int i = 1; i < values.size(); i++) {
				PyObject next = values.get(i);
				CAstNode nextNode = visit(next, context);

				result = switch (opName) {
					case "Or" -> ast.makeNode(CAstNode.IF_EXPR, result, nextNode, ast.makeConstant(false));
					case "And" -> ast.makeNode(
							CAstNode.IF_EXPR,
							ast.makeNode(CAstNode.UNARY_EXPR, CAstOperator.OP_NOT, result),
							ast.makeConstant(true),
							nextNode);
					default -> {
						assert false;
						yield null;
					}
				};
			}

			return result;
		}

		public CAstNode visitWhile(PyObject wl, WalkContext context) {
			PyObject test = (PyObject) wl.getAttr("test");
			CAstNode testNode = visit(test, context);
			
			CAstNode loopNode = visitLoopCore(wl, context, testNode, null);
			
			return loopNode;
		}

		private CAstNode visitLoopCore(PyObject loop, WalkContext context, CAstNode testNode, CAstNode update) {
			@SuppressWarnings("unchecked")
			List<PyObject> body = (List<PyObject>) loop.getAttr("body");
			@SuppressWarnings("unchecked")
			List<PyObject> orelse = (List<PyObject>) loop.getAttr("orelse");
			
			PyObject contLabel = runit("ast.Pass()");
			PyObject breakLabel = runit("ast.Pass()");
			LoopContext lc = new LoopContext(context, breakLabel, contLabel);
			
			CAstNode bodyNode = visit(CAstNode.BLOCK_STMT, body, lc);
			
			if (lc.continued) {
				CAstNode cn = ast.makeNode(CAstNode.LABEL_STMT, ast.makeConstant("label_" + label++),visit(contLabel, context));
				context.cfg().map(contLabel, cn);
				bodyNode = ast.makeNode(CAstNode.BLOCK_STMT, bodyNode, cn);
				if (update != null) {
					bodyNode = ast.makeNode(CAstNode.BLOCK_STMT, bodyNode, update);
				}
			}
			CAstNode loopNode = ast.makeNode(CAstNode.LOOP, testNode, bodyNode);
			
			if (orelse.size() > 0) {
				CAstNode elseNode = visit(CAstNode.BLOCK_STMT, orelse, context);
				loopNode = ast.makeNode(CAstNode.BLOCK_STMT, loopNode, elseNode);
			}
			
			if (lc.broke) {
				CAstNode bn = ast.makeNode(CAstNode.LABEL_STMT, ast.makeConstant("label_" + label++), visit(breakLabel, context));
				context.cfg().map(breakLabel, bn);
				loopNode = ast.makeNode(CAstNode.BLOCK_STMT, loopNode, bn);
			}
			return loopNode;
		}

		private CAstOperator translateOperator(String next) {
			switch (next) {
			case "Invert":
			case "Not":
				return CAstOperator.OP_NOT;
			case "UAdd":
				return CAstOperator.OP_ADD;
			case "USub":
				return CAstOperator.OP_SUB;
			case "Is":
			case "Eq":
				return CAstOperator.OP_EQ;
			case "Gt":
				return CAstOperator.OP_GT;
			case "GtE":
				return CAstOperator.OP_GE;
			case "Lt":
				return CAstOperator.OP_LT;
			case "LtE":
				return CAstOperator.OP_LE;
			case "IsNot":
			case "NotEq":
				return CAstOperator.OP_NE;
			case "In":
				return CAstOperator.OP_IN;
			case "NotIn":
				return CAstOperator.OP_NOT_IN;
			case "Add":
				return CAstOperator.OP_ADD;
			case "BitAnd":
				return CAstOperator.OP_BIT_AND;
			case "BitOr":
				return CAstOperator.OP_BIT_OR;
			case "BitXor":
				return CAstOperator.OP_BIT_XOR;
			case "Div":
				return CAstOperator.OP_DIV;
			case "FloorDiv":
				return CAstOperator.OP_DIV; // FIXME: need 'quotient'
			case "LShift":
				return CAstOperator.OP_LSH;
			case "Mod":
				return CAstOperator.OP_MOD;
			case "MatMult":
				return CAstOperator.OP_MUL; // FIXME: matrix multiply
			case "Mult":
				return CAstOperator.OP_MUL;
			case "Pow":
				return CAstOperator.OP_POW;
			case "RShift":
				return CAstOperator.OP_RSH;
			case "Sub":
				return CAstOperator.OP_SUB;

			default:
				assert false : next;
				return null;
			}
		}

		public CAstNode visitCompare(PyObject cmp, WalkContext context) {
			CAstNode expr = null;
			
			PyObject lhs = (PyObject) cmp.getAttr("left");
			CAstNode ln = visit(lhs, context);
			
			@SuppressWarnings("unchecked")
			Iterator<PyObject> exprs = ((List<PyObject>) cmp.getAttr("comparators")).iterator();
	
			@SuppressWarnings("unchecked")
			Iterator<PyObject> ops = ((List<PyObject>) cmp.getAttr("ops")).iterator();
	
			while (ops.hasNext()) {
				assert exprs.hasNext();
				CAstNode op = translateOperator(ops.next().getAttr("__class__", PyObject.class).getAttr("__name__", String.class));
				CAstNode rhs = visit(exprs.next(), context);
				CAstNode cmpop = ast.makeNode(CAstNode.BINARY_EXPR, op, ln, rhs);
				expr = expr==null? cmpop: ast.makeNode(CAstNode.ANDOR_EXPR, CAstOperator.OP_REL_AND, cmpop);
			}
			
			return expr;
		}

		public CAstNode visitIf(PyObject ifstmt, WalkContext context) {
			@SuppressWarnings("unchecked")
			List<PyObject> body = (List<PyObject>) ifstmt.getAttr("body");
			@SuppressWarnings("unchecked")
			List<PyObject> orelse = (List<PyObject>) ifstmt.getAttr("orelse");
			return ast.makeNode(CAstNode.IF_STMT, 
					visit(ifstmt.getAttr("test", PyObject.class), context),
					visit(CAstNode.BLOCK_STMT, body, context),
					visit(CAstNode.BLOCK_STMT, orelse, context));
		}

    public CAstNode visitIfExp(PyObject ifstmt, WalkContext context) {
			return ast.makeNode(CAstNode.IF_STMT, 
					visit(ifstmt.getAttr("test", PyObject.class), context),
					visit(ifstmt.getAttr("body", PyObject.class), context),
					visit(ifstmt.getAttr("orelse", PyObject.class), context));
    }

		public CAstNode visitBreak(PyObject brkstmt, WalkContext context) {
			CAstNode gt = ast.makeNode(CAstNode.GOTO);
			context.cfg().map(brkstmt, gt);
			context.cfg().add(brkstmt, context.getBreakFor(null), null);
			return gt;
		}

		public CAstNode visitContinue(PyObject contstmt, WalkContext context) {
			CAstNode gt = ast.makeNode(CAstNode.GOTO);
			context.cfg().map(contstmt, gt);
			context.cfg().add(contstmt, context.getContinueFor(null), null);
			return gt;
		}

		public CAstNode visitBinOp(PyObject binop, WalkContext context) {
			PyObject left= binop.getAttr("left", PyObject.class);
			PyObject right = binop.getAttr("right", PyObject.class);
			return ast.makeNode(CAstNode.BINARY_EXPR, 
					translateOperator(binop.getAttr("op", PyObject.class).getAttr("__class__", PyObject.class).getAttr("__name__", String.class)),
					visit(left, context),
					visit(right, context));
		}
 
		public CAstNode visitImport(PyObject importStmt, WalkContext context) {
			@SuppressWarnings("unchecked")
			List<PyObject> body = (List<PyObject>) importStmt.getAttr("names");		
			return ast.makeNode(CAstNode.BLOCK_STMT, body.stream().map(s -> {
				String importedName = s.getAttr("name", String.class);
				String declName = s.getAttr("asname", String.class);             
				return 
					ast.makeNode(CAstNode.DECL_STMT,
						ast.makeConstant(new CAstSymbolImpl(declName==null? importedName: declName, PythonCAstToIRTranslator.Any)),
						ast.makeNode(
							CAstNode.PRIMITIVE,
	                            ast.makeConstant("import"),
	                            ast.makeConstant(importedName)));
			}).collect(Collectors.toList()));
		}
		
		public CAstNode visitDelete(PyObject deleteStmt, WalkContext context) {
			int i = 0;
			@SuppressWarnings("unchecked")
			List<PyObject> targets = (List<PyObject>) deleteStmt.getAttr("targets");

			CAstNode[] deletes = new CAstNode[targets.size()];
			for (PyObject target : targets) {
				deletes[i++] = ast.makeNode(CAstNode.CALL, ast.makeConstant("delete"), ast.makeConstant(target));
			}

			return ast.makeNode(CAstNode.BLOCK_STMT, deletes);
		}

		public CAstNode visitDict(PyObject dict, WalkContext context) {
			List<CAstNode> x = new LinkedList<>();
			x.add(ast.makeNode(CAstNode.NEW, ast.makeConstant("dict")));
			
			@SuppressWarnings("unchecked")
			Iterator<PyObject> keys = ((List<PyObject>) dict.getAttr("keys")).iterator();		
			@SuppressWarnings("unchecked")
			Iterator<PyObject> values = ((List<PyObject>) dict.getAttr("values")).iterator();		
			while(keys.hasNext()) {
				x.add(visit(keys.next(), context));
				x.add(visit(values.next(), context));
			}
			return ast.makeNode(CAstNode.OBJECT_LITERAL, x);
		}

		public CAstNode visitAttribute(PyObject attr, WalkContext context) {
			CAstNode obj = visit(attr.getAttr("value", PyObject.class), context);
			String field = attr.getAttr("attr", String.class);
			return ast.makeNode(CAstNode.OBJECT_REF, obj, ast.makeConstant(field));
		}
		
		public CAstNode visitLambda(PyObject lambda, WalkContext context) {
			return ast.makeNode(CAstNode.EMPTY);
		}
		
		public CAstNode handleList(String type, String field, PyObject list, WalkContext context) {
			List<CAstNode> x = new LinkedList<>();
			x.add(ast.makeNode(CAstNode.NEW, ast.makeConstant(type)));
			
			int n = 0;
			@SuppressWarnings("unchecked")
			Iterator<PyObject> values = ((List<PyObject>) list.getAttr("elts")).iterator();		
			while(values.hasNext()) {
				x.add(ast.makeConstant(n++));
				x.add(visit(values.next(), context));
			}
			return ast.makeNode(CAstNode.OBJECT_LITERAL, x);	
		}

		public CAstNode visitList(PyObject list, WalkContext context) {
			return handleList("list", "elts", list, context);
		}
		
		public CAstNode visitUnaryOp(PyObject unop, WalkContext context) {
			PyObject op = unop.getAttr("operand", PyObject.class);
			return ast.makeNode(CAstNode.UNARY_EXPR, 
					translateOperator(unop.getAttr("op", PyObject.class).getAttr("__class__", PyObject.class).getAttr("__name__", String.class)),
					visit(op, context));
		}

		public CAstNode visitTuple(PyObject tp, WalkContext context) {
			return handleList("tuple", "elts", tp, context);
		}

		public CAstNode visitFor(PyObject fl, WalkContext context) {
      return handleFor(fl,context);
		}

		public CAstNode visitAsyncFor(PyObject fl, WalkContext context) {
      return handleFor(fl,context);
		}

		private CAstNode handleTargetAndIter(CAstNode target, CAstNode iter, CAstNode body){
			CAstNode result = body; 
			String tempName = "temp " + ++tmpIndex;
			CAstNode test =
			ast.makeNode(
				CAstNode.BINARY_EXPR,
				CAstOperator.OP_NE,
				ast.makeConstant(null),
				ast.makeNode(
					CAstNode.BLOCK_EXPR,
					ast.makeNode(
						CAstNode.ASSIGN,
						target,
						ast.makeNode(
							CAstNode.EACH_ELEMENT_GET,
							ast.makeNode(CAstNode.VAR, ast.makeConstant(tempName)),
							iter)
						)
					));
			result = 
				ast.makeNode(
					CAstNode.BLOCK_STMT,
					ast.makeNode(
						CAstNode.DECL_STMT,
						ast.makeConstant(
							new CAstSymbolImpl(tempName, PythonCAstToIRTranslator.Any)),
						iter),
					ast.makeNode(
						CAstNode.ASSIGN,
						target,
						ast.makeNode(
							CAstNode.EACH_ELEMENT_GET,
							ast.makeNode(CAstNode.VAR, ast.makeConstant(tempName)),
							ast.makeConstant(null))),
					ast.makeNode(
						CAstNode.LOOP,
						test,
						ast.makeNode(
							CAstNode.BLOCK_STMT,
							ast.makeNode(
								CAstNode.ASSIGN,
								target,
								ast.makeNode(
										CAstNode.OBJECT_REF,
										ast.makeNode(CAstNode.VAR, ast.makeConstant(tempName)),
										target)),
							result)));
			return result;
		}

		private CAstNode handleFor(PyObject fl, WalkContext context){
			@SuppressWarnings("unchecked")
			List<PyObject> orelse = (List<PyObject>) fl.getAttr("orelse");
			@SuppressWarnings("unchecked")
			List<PyObject> body = (List<PyObject>) fl.getAttr("body");
			CAstNode target = visit(fl.getAttr("target", PyObject.class), context);
			CAstNode iter = visit(fl.getAttr("iter", PyObject.class), context);

			PyObject contLabel = runit("ast.Pass()");
			PyObject breakLabel = runit("ast.Pass()");
			LoopContext lc = new LoopContext(context, breakLabel, contLabel);
			
			CAstNode bodyNode = visit(CAstNode.BLOCK_STMT, body, lc);
			
			if (lc.continued) {
				CAstNode cn = ast.makeNode(CAstNode.LABEL_STMT, ast.makeConstant("label_" + label++),visit(contLabel, context));
				context.cfg().map(contLabel, cn);
				bodyNode = ast.makeNode(CAstNode.BLOCK_STMT, bodyNode, cn);
			}

			// Making loop and test node for target an iter
			CAstNode loopNode = handleTargetAndIter(target, iter, bodyNode);

			if (!orelse.isEmpty()) {
				CAstNode elseNode = visit(CAstNode.BLOCK_STMT, orelse, context);
				loopNode = ast.makeNode(CAstNode.BLOCK_STMT, loopNode, elseNode);
			}
			
			if (lc.broke) {
				CAstNode bn = ast.makeNode(CAstNode.LABEL_STMT, ast.makeConstant("label_" + label++), visit(breakLabel, context));
				context.cfg().map(breakLabel, bn);
				loopNode = ast.makeNode(CAstNode.BLOCK_STMT, loopNode, bn);
			}
			return loopNode;
    }

		public CAstNode visitWith(PyObject o, WalkContext context) {
      return handleWith(o,context);
		}

		public CAstNode visitAsyncWith(PyObject o, WalkContext context) {
      return handleWith(o,context);
		}

		private CAstNode handleWith(PyObject o, WalkContext context){
			@SuppressWarnings("unchecked")
			List<PyObject> items = (List<PyObject>) o.getAttr("items");
			@SuppressWarnings("unchecked")
			List<PyObject> body = (List<PyObject>) o.getAttr("body");

			CAstNode bodyAst = ast.makeNode(CAstNode.BLOCK_STMT, body.stream().map(f -> visit(f, context)).collect(Collectors.toList()));

      for (PyObject wi : items) {
				String tmpName = "tmp_" + ++tmpIndex;

				CAstNode context_expr = visit(wi.getAttr("context_expr", PyObject.class), context);
				CAstNode optional_vars = wi.getAttr("optional_vars", PyObject.class) != null
					? visit(wi.getAttr("optional_vars", PyObject.class), context):
					ast.makeNode(CAstNode.VAR, ast.makeConstant(tmpName));

        bodyAst =
            ast.makeNode(
                CAstNode.BLOCK_STMT,
                ast.makeNode(
                    CAstNode.DECL_STMT,
                    ast.makeConstant(new CAstSymbolImpl(tmpName, PythonCAstToIRTranslator.Any))),
								optional_vars.getKind() == CAstNode.VAR
										? ast.makeNode(
												CAstNode.DECL_STMT,
												ast.makeConstant(
														new CAstSymbolImpl(
																optional_vars.getChild(0).getValue().toString(),
																PythonCAstToIRTranslator.Any)),
												context_expr)
										: ast.makeNode(
												CAstNode.ASSIGN, optional_vars, context_expr),
                ast.makeNode(
                    CAstNode.UNWIND,
                    ast.makeNode(
                        CAstNode.BLOCK_EXPR,
												ast.makeNode(
														CAstNode.CALL,
														ast.makeNode(
																CAstNode.OBJECT_REF, optional_vars, ast.makeConstant("__begin__")),
														ast.makeNode(CAstNode.EMPTY)),
												bodyAst),
										ast.makeNode(
												CAstNode.CALL,
												ast.makeNode(CAstNode.OBJECT_REF, optional_vars, ast.makeConstant("__end__")),
												ast.makeNode(CAstNode.EMPTY))));
      }

      return bodyAst;
		}
		
		public CAstNode visitSet(PyObject set, WalkContext context) {
			return handleList("set", "elts", set, context);
		}

		public CAstNode visitSlice(PyObject slice, WalkContext context) {
			CAstNode lower = visit(slice.getAttr("lower", PyObject.class), context);
			CAstNode upper = visit(slice.getAttr("upper", PyObject.class), context);
			CAstNode step;
			try {
				step = visit(slice.getAttr("step", PyObject.class), context);
			} catch (Exception e) {
				step = null;
			}

			if (step == null) {
				return ast.makeNode(CAstNode.ARRAY_LITERAL, lower, upper);
			} else {
				return ast.makeNode(CAstNode.ARRAY_LITERAL, lower, upper, step);
			}
		}


		public CAstNode visitSubscript(PyObject subscript, WalkContext context) {
			CAstNode obj =  visit(subscript.getAttr("value", PyObject.class), context);
			CAstNode f =  visit(subscript.getAttr("slice", PyObject.class), context);
			return ast.makeNode(CAstNode.OBJECT_REF, obj, f);
		}

		public CAstNode visitPass(PyObject pass, WalkContext context) {
			return ast.makeNode(CAstNode.EMPTY);
		}
	
		public CAstNode visitReturn(PyObject ret, WalkContext context) {
			return ast.makeNode(CAstNode.RETURN, visit(ret.getAttr("value", PyObject.class), context));
		}
		
		public CAstNode visitGlobal(PyObject global, WalkContext context) {
			Scope s = context.scope();
			@SuppressWarnings("unchecked")
			List<String> names = (List<String>) global.getAttr("names");
			s.globalNames.addAll(names);
			return ast.makeNode(CAstNode.EMPTY);
		}
		
		public CAstNode visitNonlocal(PyObject nonlocal, WalkContext context) {
			Scope s = context.scope();
			@SuppressWarnings("unchecked")
			List<String> names = (List<String>) nonlocal.getAttr("names");
			s.nonLocalNames.addAll(names);
			return ast.makeNode(CAstNode.EMPTY);
		}

		public CAstNode visitJoinedStr(PyObject arg0, WalkContext context) throws Exception {
//			@SuppressWarnings("unchecked")
//			Iterator<PyObject> values = ((List<PyObject>) arg0.getAttr("values")).iterator();
//
//			List<CAstNode> cs = new ArrayList<>();
//			while (values.hasNext()) {
//				CAstNode value = visitExpr(values.next(), context);
//				cs.add(value);
//			}
			return ast.makeNode(CAstNode.EMPTY);
		}

		private int tmpIndex = 0;
		public CAstNode visitDictComp(PyObject arg0, WalkContext context) throws Exception {
			String dictName = "temp " + tmpIndex++;
			CAstNode body =
					ast.makeNode(
							CAstNode.ASSIGN,
							ast.makeNode(
									CAstNode.OBJECT_REF,
									ast.makeNode(CAstNode.VAR, ast.makeConstant(dictName)),
									visit(arg0.getAttr("key", PyObject.class), context)),
							visit(arg0.getAttr("value", PyObject.class), context));

			return ast.makeNode(
					CAstNode.BLOCK_EXPR,
					ast.makeNode(
							CAstNode.DECL_STMT,
							ast.makeConstant(new CAstSymbolImpl(dictName, PythonCAstToIRTranslator.Any)),
							ast.makeNode(CAstNode.NEW, ast.makeConstant(PythonTypes.dict))),
					doGenerators((List<PyObject>) arg0.getAttr("generators"), body, context),
					ast.makeNode(CAstNode.VAR, ast.makeConstant(dictName)));
		}

		private CAstNode doGenerators(List<PyObject> generators, CAstNode body, WalkContext context)
				throws Exception {
			CAstNode result = body;

			for (PyObject c : generators) {
				try {
					List<PyObject> ifs = (List<PyObject>) c.getAttr("ifs");
					int j = ifs.size();
					if (j > 0) {
						for (PyObject test : ifs) {
							result = ast.makeNode(CAstNode.IF_EXPR, visit(test, context), body);
						}
					}
				} catch (NullPointerException e) {
					System.out.printf("generator null %s\n", e.getMessage());
					continue;
				}

				String tempName = "temp " + ++tmpIndex;

				CAstNode test =
						ast.makeNode(
								CAstNode.BINARY_EXPR,
								CAstOperator.OP_NE,
								ast.makeConstant(null),
								ast.makeNode(
										CAstNode.BLOCK_EXPR,
										ast.makeNode(
												CAstNode.ASSIGN,
												visit(c.getAttr("target", PyObject.class), context),
												ast.makeNode(
														CAstNode.EACH_ELEMENT_GET,
														ast.makeNode(CAstNode.VAR, ast.makeConstant(tempName)),
														visit(c.getAttr("target", PyObject.class), context)))));

				result = ast.makeNode(
						CAstNode.BLOCK_EXPR,
						ast.makeNode(
								CAstNode.DECL_STMT,
								ast.makeConstant(new CAstSymbolImpl(tempName, PythonCAstToIRTranslator.Any)),
								visit(c.getAttr("iter", PyObject.class), context)),
						ast.makeNode(
								CAstNode.ASSIGN,
								visit(c.getAttr("target", PyObject.class), context),
								ast.makeNode(
										CAstNode.EACH_ELEMENT_GET,
										ast.makeNode(CAstNode.VAR, ast.makeConstant(tempName)),
										ast.makeConstant(null))),
						ast.makeNode(
								CAstNode.LOOP,
								test,
								ast.makeNode(
										CAstNode.BLOCK_EXPR,
										ast.makeNode(
												CAstNode.ASSIGN,
												visit(c.getAttr("target", PyObject.class), context),
												ast.makeNode(
														CAstNode.OBJECT_REF,
														ast.makeNode(CAstNode.VAR, ast.makeConstant(tempName)),
														visit(c.getAttr("target", PyObject.class), context))),
										result)));
			}

			return result;
		}

		public CAstNode visitAnnAssign(PyObject arg0, WalkContext context) throws Exception {
			// TODO: Handle simple attr
			try {
				return ast.makeNode(CAstNode.ASSIGN,
						visit(arg0.getAttr("target", PyObject.class), context),
						visit(arg0.getAttr("annotation", PyObject.class), context),
						visit(arg0.getAttr("value", PyObject.class), context));
			} catch (NullPointerException e) {
				return ast.makeNode(CAstNode.ASSIGN,
						visit(arg0.getAttr("target", PyObject.class), context),
						visit(arg0.getAttr("annotation", PyObject.class), context));
			}
		}

		public CAstNode visitAugAssign(PyObject arg0, WalkContext context) throws Exception {
			return ast.makeNode(CAstNode.ASSIGN_PRE_OP,
					visit(arg0.getAttr("target", PyObject.class), context),
					visit(arg0.getAttr("value", PyObject.class), context),
					translateOperator(arg0.getAttr("op", PyObject.class).getAttr("__class__", PyObject.class).getAttr("__name__", String.class)));
		}

		public CAstNode visitAssert(PyObject arg0, WalkContext context) throws Exception {
			// TODO: Note position
			try {
				PyObject msg = arg0.getAttr("msg", PyObject.class);
				return ast.makeNode(CAstNode.ASSERT, visit(arg0.getAttr("test", PyObject.class), context), visit(msg, context));
			} catch (NullPointerException e) {
				return ast.makeNode(CAstNode.ASSERT, visit(arg0.getAttr("test", PyObject.class), context));
			}
		}

		public CAstNode visitAsyncFunctionDef(PyObject arg0, WalkContext context) throws Exception {
			return visitFunctionDef(arg0, context);
		}

		public CAstNode visitAwait(PyObject arg0, WalkContext context) throws Exception {
			return visitExpr(arg0, context);
		}

		public CAstNode visitClassDef(PyObject arg0, WalkContext context) throws Exception {
			return ast.makeNode(CAstNode.EMPTY);
			/*
			WalkContext parent = context;

		      CAstType.Class cls =
		          new CAstType.Class() {
		            @Override
		            public Collection<CAstType> getSupertypes() {
		              Collection<CAstType> supertypes = HashSetFactory.make();
		              for (expr e : arg0.getInternalBases()) {
		                try {
		                  CAstType type = types.getCAstTypeFor(e.getText());
		                  if (type != null) {
		                    supertypes.add(type);
		                  } else {
		                    supertypes.add(getMissingType(e.getText()));
		                  }
		                } catch (Exception e1) {
		                  assert false : e1;
		                }
		              }
		              return supertypes;
		            }

		            @Override
		            public String getName() {
		              return arg0.getInternalName();
		            }

		            @Override
		            public boolean isInterface() {
		              return false;
		            }

		            @Override
		            public Collection<CAstQualifier> getQualifiers() {
		              return Collections.emptySet();
		            }
		          };
		      // TODO: CURRENTLY THIS WILL NOT BE CORRECT FOR EXTENDING CLASSES IMPORTED FROM ANOTHER MODULE
		      types.map(arg0.getInternalName(), cls);

		      Collection<CAstEntity> members = HashSetFactory.make();

		      CAstEntity clse =
		          new AbstractClassEntity(cls) {

		            @Override
		            public int getKind() {
		              return CAstEntity.TYPE_ENTITY;
		            }

		            @Override
		            public String getName() {
		              return cls.getName();
		            }

		            @Override
		            public CAstType getType() {
		              return cls;
		            }

		            @Override
		            public Map<CAstNode, Collection<CAstEntity>> getAllScopedEntities() {
		              return Collections.singletonMap(null, members);
		            }

		            @Override
		            public Position getPosition(int arg) {
		              return null;
		            }

		            @Override
		            public Position getPosition() {
		              return makePosition(arg0);
		            }

		            @Override
		            public Position getNamePosition() {
		              return makePosition(arg0.getInternalNameNode());
		            }
		          };

		      WalkContext child =
		          new WalkContext() {
		            private final CAstSourcePositionRecorder pos = new CAstSourcePositionRecorder();

		            @Override
		            public Map<CAstNode, Collection<CAstEntity>> getScopedEntities() {
		              return Collections.singletonMap(null, members);
		            }

		            @Override
		            public PythonTree top() {
		              return arg0;
		            }

		            @Override
		            public void addScopedEntity(CAstNode newNode, CAstEntity visit) {
		              members.add(visit);
		            }

		            private WalkContext codeParent() {
		              WalkContext p = parent;
		              while (p.entity().getKind() == CAstEntity.TYPE_ENTITY) {
		                p = p.getParent();
		              }
		              return p;
		            }

		            @Override
		            public CAstControlFlowRecorder cfg() {
		              return (CAstControlFlowRecorder) codeParent().entity().getControlFlow();
		            }

		            @Override
		            public CAstSourcePositionRecorder pos() {
		              return pos;
		            }

		            @Override
		            public CAstNodeTypeMapRecorder getNodeTypeMap() {
		              return codeParent().getNodeTypeMap();
		            }

		            @Override
		            public PythonTree getContinueFor(String label) {
		              assert false;
		              return null;
		            }

		            @Override
		            public PythonTree getBreakFor(String label) {
		              assert false;
		              return null;
		            }

		            @Override
		            public CAstEntity entity() {
		              return clse;
		            }

		            @Override
		            public WalkContext getParent() {
		              return parent;
		            }
		          };

		      URL url = this.url();
		      TestVisitor v = new TestVisitor(clse) {
				@Override
				public URL url() {
					return url;
				} };
		      for (stmt e : arg0.getInternalBody()) {
		        if (!(e instanceof Pass)) {
		          e.accept(v);
		        }
		      }

		      CAstNode x = Ast.makeNode(CAstNode.CLASS_STMT, Ast.makeConstant(clse));
		      context.addScopedEntity(x, clse);
		      return x;
		    */
		    }

		public CAstNode visitMatch(PyObject match, WalkContext context) {
			CAstNode subject = visit((PyObject) match.getAttr("subject"), context);

			@SuppressWarnings("unchecked")
			List<PyObject> cases = (List<PyObject>) match.getAttr("cases");

			return handleMatchCases(subject, cases, context);
		}

		public CAstNode handleMatchCases(CAstNode subject, List<PyObject> matchCases, WalkContext context) {
			PyObject matchCase = matchCases.removeFirst();

			CAstNode pattern = visit((PyObject) matchCase.getAttr("pattern"), context);

			PyObject guard = (PyObject) matchCase.getAttr("guard", PyObject.class);

			CAstNode body = visit(CAstNode.BLOCK_STMT,
					((List<PyObject>) matchCase.getAttr("body")), context);

			CAstNode ifTest = ast.makeNode(CAstNode.BINARY_EXPR,
					CAstOperator.OP_EQ,
					subject,
					pattern);

			if (guard != null) {
				ifTest = ast.makeNode(CAstNode.BINARY_EXPR,
						CAstOperator.OP_BIT_AND,
						ifTest,
						visit(guard, context));
			}

			if (matchCases.isEmpty()) {
				return ast.makeNode(CAstNode.IF_STMT,
						ifTest,
						body);
			} else {
				return ast.makeNode(CAstNode.IF_STMT,
						ifTest,
						body,
						handleMatchCases(subject, matchCases, context));
			}
		}

		public CAstNode visitMatchValue(PyObject matchValue, WalkContext context) {
			return visit((PyObject) matchValue.getAttr("value"), context);
		}

		public CAstNode visitMatchSingleton(PyObject matchSingleton, WalkContext context) {
			Object value = matchSingleton.getAttr("value");
			return ast.makeConstant(value);
		}

		public CAstNode visitMatchSequence(PyObject matchSequence, WalkContext context) {
			List<PyObject> patterns = (List<PyObject>) matchSequence.getAttr("patterns");

			for (PyObject patt : patterns) {
				CAstNode pattern = visit(patt, context);
			}

			return ast.makeNode(CAstNode.EMPTY);
		}

		public CAstNode visitMatchMapping(PyObject matchMapping, WalkContext context) {
			return ast.makeNode(CAstNode.EMPTY);
		}

		public CAstNode visitMatchClass(PyObject matchClass, WalkContext context) {
			return ast.makeNode(CAstNode.EMPTY);
		}

		public CAstNode visitMatchStar(PyObject matchStar, WalkContext context) {
			return ast.makeNode(CAstNode.EMPTY);
		}

		public CAstNode visitMatchAs(PyObject matchAs, WalkContext context) {
			return ast.makeNode(CAstNode.EMPTY);
		}

		public CAstNode visitMatchOr(PyObject matchOr, WalkContext context) {
			return ast.makeNode(CAstNode.EMPTY);
		}

		public CAstNode visitYield(PyObject yield, WalkContext context) {
			PyObject yieldValue = yield.getAttr("value", PyObject.class);

			if (yieldValue == null) {
				return ast.makeNode(CAstNode.RETURN_WITHOUT_BRANCH);
			}
			return visit(yieldValue, context);
		}
			
		private Set<String> exposedNames(CAstNode tree) {
			return nm.new Matcher()
	        	.findAll(
	        			new Context() {
	        				@Override
	        				public CAstEntity top() {
	        					return entity;
	        				}

	              @Override
	              public CAstSourcePositionMap getSourceMap() {
	                return entity.getSourceMap();
	              }
	            },
	            tree).stream().map(s -> (String)((CAstNode)s.get("n")).getValue()).collect(Collectors.toSet());
		}
	}
	
	public static IClassHierarchy load(Set<SourceModule> files) throws ClassHierarchyException {
		PythonLoaderFactory loaders = new JepPythonLoaderFactory();

		AnalysisScope scope =
				new AnalysisScope(Collections.singleton(PythonLanguage.Python)) {
			{
				loadersByName.put(PythonTypes.pythonLoaderName, PythonTypes.pythonLoader);
				loadersByName.put(
						SYNTHETIC,
						new ClassLoaderReference(
								SYNTHETIC, PythonLanguage.Python.getName(), PythonTypes.pythonLoader));
			}
		};

		for (SourceModule fn : files) {
			scope.addToScope(PythonTypes.pythonLoader, fn);
		}

		return SeqClassHierarchyFactory.make(scope, loaders);		
	}
	
	// example of using this api
	public static void main(String... args) throws IOException, Error, ClassHierarchyException {
		IRFactory<IMethod> irs = AstIRFactory.makeDefaultFactory();
		
		Set<SourceModule> sources = Arrays.stream(args).map(file -> new SourceFileModule(new File(file), file, null)).collect(Collectors.toSet());
		
		IClassHierarchy cha = load(sources);
		
		cha.forEach(c -> {
			System.err.println(c);
			c.getDeclaredMethods().forEach(m -> {
				System.err.println(m);
				System.err.println(irs.makeIR(m, Everywhere.EVERYWHERE, SSAOptions.defaultOptions()));
			});
		});
		
	}

	@Override
	public <C extends RewriteContext<K>, K extends CopyKey<K>> void addRewriter(CAstRewriterFactory<C, K> factory,
			boolean prepend) {
		// TODO Auto-generated method stub
		
	}

	private String fn;
	private Path pn;
	
	public CPythonAstToCAstTranslator(String fn) {
		this.fn = fn;
		this.pn = Path.of(fn);
	}

	@Override
	public CAstEntity translateToCAst() throws Error, IOException {
		return new PythonScriptEntity(fn, pn);				
	}
}

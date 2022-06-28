package de.lego.gottfried.decdat.decompiler;

import static de.lego.gottfried.decdat.dat.DatSymbol.Type.Func;
import static de.lego.gottfried.decdat.dat.DatSymbol.Type.Instance;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Stack;
import java.util.List;

import de.lego.gottfried.decdat.MainForm;
import de.lego.gottfried.decdat.dat.DatSymbol;
import de.lego.gottfried.decdat.token.Token;
import de.lego.gottfried.decdat.token.TokenArray;
import de.lego.gottfried.decdat.token.TokenEnum;
import de.lego.gottfried.decdat.token.TokenIntParam;
import de.lego.gottfried.decdat.token.TokenSymbolParam;
import de.lego.gottfried.util.Pair;

public class Function {
	private Token[] code;
	private Token currTok;
	private int index;
	private LinkedList<Pair<Integer, String>> lines = new LinkedList<Pair<Integer, String>>();

	private boolean retParam;

	private String decompilePush(int forceParam) {
		Token t = code[index];
		if (t instanceof TokenSymbolParam) {
			TokenSymbolParam ts = (TokenSymbolParam) t;
			String ret;
			if (ts.sym.hasFlags(DatSymbol.Flag.Classvar)) {
				t = code[--index];
				if (t.op == TokenEnum.zPAR_TOK_SETINSTANCE) {
					DatSymbol sym = ((TokenSymbolParam) t).sym;
					ret = sym.localName() + "." + ts.sym.localName();
				} else {
					ret = ts.sym.localName();
					++index;
				}
			} else if (ts.sym.name.charAt(0) == '�') {
				if (ts.sym.name.equalsIgnoreCase("�INSTANCE_HELP"))
					ret = "NULL";
				else {
					ret = '"' + (String) ts.sym.content[0] + '"';
				}
			} else
				ret = ts.sym.localName();
			if (ts instanceof TokenArray)
				ret += "[" + ((TokenArray) ts).offset + "]";
			return ret;
		}

		if (forceParam == Instance || forceParam == Func) {
			int i = ((TokenIntParam) t).param;
			if (i == -1) {
				return forceParam == Func ? "NOFUNC" : "-1";
			}
			return MainForm.theDat.Symbols[i].localName();
		} else if (forceParam == DatSymbol.Type.Float) {
			return "" + Float.intBitsToFloat(((TokenIntParam) t).param);
		}

		// Auronen
		int intParam = (Integer) ((TokenIntParam) t).param;

		if (intParam > 60 )
		{
			// return the name of the symbol under the id instead
			List<DatSymbol> col;
			col = MainForm.theDat.getAllSymbolIDs(String.valueOf(intParam));
			
			if (col.size() == 0)
			{
				return ((Integer) ((TokenIntParam) t).param).toString();
			}
			else if ( col.get(0).type() == Instance && !col.get(0).name.contains(".par")) {
				return col.get(0).name + " /*" + ((Integer) ((TokenIntParam) t).param).toString() + "*/";
			} else {
				return ((Integer) ((TokenIntParam) t).param).toString();
			}
		}
		else
		{
			return ((Integer) ((TokenIntParam) t).param).toString();
		}

	}

	private String decompileParameter(int forceParam) {
		Token t = code[index];
		switch (t.op.Type) {
			case PUSH:
				return decompilePush(forceParam);
			case CALL:
				return decompileCall();
			case BINOP:
			case UNOP:
				return decompileOperation(0);
			default:
				break;
		}
		return "<?>";
	}

	private String decompileCall() {
		TokenSymbolParam call = (TokenSymbolParam) code[index];

		DatSymbol params[] = call.sym.getParameters();

		String ret = ")";
		for (int i = params.length - 1; i >= 0; --i) {
			--index;
			ret = decompileParameter(params[i].type()) + ret;
			if (i > 0)
				ret = ", " + ret;
		}

		return call.sym.localName() + "(" + ret;
	}

	private String decompileOperation(int parent) {
		Token t = code[index];
		boolean brackets = t.op.Precedence < parent;
		StringBuilder ret = new StringBuilder();

		if (brackets)
			ret.append('(');

		if (t.op.Type == TokenEnum.UNOP) {
			--index;
			ret.append(t.op.Operator);
			if (code[index].op.isOp())
				ret.append(decompileOperation(t.op.Precedence));
			else
				ret.append(decompileParameter(0));
		} else {
			--index;
			if (t.op.Type == TokenEnum.BINOP && code[index].op.isOp())
				ret.append(decompileOperation(t.op.Precedence));
			else
				ret.append(decompileParameter(0));

			ret.append(' ');
			ret.append(t.op.Operator);
			ret.append(' ');

			int ptype = 0;
			if (t.op == TokenEnum.zPAR_TOK_ASSIGNFLOAT)
				ptype = DatSymbol.Type.Float;
			else if (t.op == TokenEnum.zPAR_TOK_ASSIGNFUNC)
				ptype = DatSymbol.Type.Func;
			else if (t.op == TokenEnum.zPAR_TOK_ASSIGNINST)
				ptype = DatSymbol.Type.Instance;

			--index;
			if (t.op.Type == TokenEnum.BINOP && code[index].op.isOp())
				ret.append(decompileOperation(t.op.Precedence));
			else
				ret.append(decompileParameter(ptype));
		}

		if (brackets)
			ret.append(')');

		return ret.toString();
	}

	private String decompileReturn() {
		String ret = "return";
		if (retParam) {
			--index;
			ret += ' ' + decompileParameter(0);
		}
		return ret + ';';
	}

	private HashSet<Integer> endif = new HashSet<Integer>();
	private Stack<Integer> ifmark = new Stack<Integer>();
	private boolean lifon = false;

	private void decompileCondition() {
		int t = 1;
		int tpos = ((TokenIntParam) code[index]).param;
		int sub = 1;
		boolean nosem = false;
		Pair<Integer, String> p;

		if (code[index].op == TokenEnum.zPAR_TOK_JUMP) {
			ifmark.push(code[index].stackPtr);
			if (!endif.contains(tpos)) {
				endif.add(tpos);
				if (lines.size() > 0 && (p = lines.get(0)).right.startsWith("if") && lifon) {
					sub = 0;
					p.right = "else " + p.right;
					return;
				} else
					add("else {");
			} else {
				return;
			}
		} else {
			if (!ifmark.isEmpty() && ifmark.peek() == (tpos - 5)) {
				nosem = true;
				tpos = ifmark.pop();
				lifon = false;
			} else
				lifon = true;
			--index;
			add("if(" + decompileParameter(0) + ") {");
		}

		if (lines.size() <= 1) {
			sub = 0;
		} else
			while ((p = lines.get(t++)).left < tpos) {
				p.right = "    " + p.right;
				if (t == lines.size()) {
					sub = 0;
					break;
				}
			}

		lines.add(t - sub, new Pair<Integer, String>(-0xbeef, "}" + (nosem ? "" : ';')));

		if (nosem)
			if ((p = lines.get(t)).right.startsWith("if")) {
				p.right = "else " + p.right;
				lifon = false;
			}
	}

	private void decompile(int start, int end, boolean returnParam) {
		retParam = returnParam;

		code = Token.DoStack(start, end);
		index = code.length - 1;

		while (index >= 0) {
			switch ((currTok = code[index]).op.Type) {
				case CALL:
					add(decompileCall() + ';');
					break;
				case RETURN:
					add(decompileReturn());
					break;
				case PUSH:
					add(decompilePush(0) + ';');
					break;
				case BINOP:
				case UNOP:
				case ASSIGN:
					add(decompileOperation(0) + ';');
					break;
				case JUMP:
					decompileCondition();
					break;
				default:
					MainForm.LogErr("unknown token?");
					break;
			}
			--index;
		}
	}

	private void add(String s) {
		lines.addFirst(new Pair<Integer, String>(currTok.stackPtr, s));
	}

	public Function(int start, int end) {
		decompile(start, end, false);
	}

	public Function(int start, int end, boolean ret) {
		decompile(start, end, ret);
	}

	public LinkedList<String> getLines() {
		LinkedList<String> s = new LinkedList<String>();
		for (Pair<Integer, String> p : lines)
			s.add(p.right);
		return s;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Pair<Integer, String> s : lines)
			sb.append(s.right).append(System.getProperty("line.separator"));
		return sb.toString();
	}
}
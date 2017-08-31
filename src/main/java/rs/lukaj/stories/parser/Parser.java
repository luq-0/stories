/*
  Stories - an interactive storytelling language
  Copyright (C) 2017 Luka Jovičić

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published
  by the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package rs.lukaj.stories.parser;

import rs.lukaj.stories.Utils;
import rs.lukaj.stories.exceptions.InterpretationException;
import rs.lukaj.stories.parser.types.*;
import rs.lukaj.stories.runtime.Chapter;
import rs.lukaj.stories.runtime.State;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import static rs.lukaj.stories.parser.Parser.LineType.*;

public class Parser {

    public enum LineType {
        ANSWER {
            @Override
            public Line parse(String line, int indent, Chapter chapter, Object... additionalParams)
                    throws InterpretationException {
                Question question = (Question)additionalParams[0];
                if(question == null) throw new InterpretationException("Creating answer without the question");
                String[] tokens = line.substring(2).split("\\s*]\\s*", 2);
                if(tokens.length < 2)
                    throw new InterpretationException("No variable for answer");
                if(chapter.imageExists(tokens[1])) {
                    PictureAnswer ans = new PictureAnswer(tokens[0], chapter.getImage(tokens[1]));
                    question.addPictureAnswer(ans);
                } else {
                    Answer ans = new Answer(chapter, tokens[0], tokens[1]);
                    question.addAnswer(ans);
                }
                //we're being quite strict here: disallowing mixing of image and String, even if
                //String only looks like an image. An alternative would be falling back to
                //String in case Question detects mixing, though I'm afraid that'd make things
                //quite complicated and unpredictable todo reconsider
                return question;
            }
        },
        STATEMENT {
            @Override
            public Line parse(String line, int indent, Chapter chapter, Object... additionalParams)
                    throws InterpretationException {
                if(line.startsWith(":")) line = line.substring(1);
                //^ it doesn't have to start with : - e.g. in statement blocks
                return Statement.create(chapter, line, indent);
            }
        },
        NARRATIVE {
            @Override
            public Line parse(String line, int indent, Chapter chapter, Object... additionalParams)
                    throws InterpretationException {
                return new Narrative(chapter, line, indent);
            }
        },
        QUESTION {
            @Override
            public Line parse(String line, int indent, Chapter chapter, Object... additionalParams)
                    throws InterpretationException {
                String[] parts = line.substring(1).split("\\s*:\\s*", 2);
                if(parts.length < 2)
                    throw new InterpretationException("Invalid question syntax; missing :");
                String text = parts[1];
                String var = Utils.between(parts[0], '[', ']');
                String time = Utils.between(parts[0], '(', ')');
                String charName = Utils.between(line, ']', ':');
                if(time != null) time = time.trim();
                if(var == null) throw new InterpretationException("Variable name for question is empty");
                charName = charName.trim(); //this shouldn't be null (both ] and : must exist, otherwise NPE would be thrown earlier)
                var = var.trim();

                if(time == null) {
                    return new Question(chapter, var, text, charName, indent);
                } else {
                    double multiplicator=1;
                    if(time.endsWith("ms")) {
                        multiplicator = 1./1000;
                        time = time.substring(0, time.length()-2);
                    } else if(time.endsWith("s")) {
                        multiplicator = 1;
                        time = time.substring(0, time.length()-1);
                    } else if(time.endsWith("m")) {
                        multiplicator = 60;
                        time = time.substring(0, time.length()-1);
                    }
                    double seconds = Double.parseDouble(time) * multiplicator;
                    return new TimedQuestion(chapter, var, text, charName, seconds, indent);
                }
            }
        },
        SPEECH {
            @Override
            public Line parse(String line, int indent, Chapter chapter, Object... additionalParams)
                    throws InterpretationException {
                String[] parts = line.split("\\s*:\\s*", 2);
                return new Speech(chapter, parts[0], parts[1], indent);
            }
        },
        INPUT {
            @Override
            public Line parse(String line, int indent, Chapter chapter, Object... additionalParams)
                    throws InterpretationException {
                String[] parts = line.split("\\s*]\\s*", 2);
                return new TextInput(chapter, parts[0].substring(1), parts[1], indent);
            }
        },
        COMMENT {
            @Override
            public Line parse(String line, int indent, Chapter chapter, Object... additionalParams)
                    throws InterpretationException {
                return new Nop(chapter, indent); //this is a no-op
            }
        },
        STATEMENT_BLOCK_MARKER {
            @Override
            public Line parse(String line, int indent, Chapter chapter, Object... additionalParams)
                    throws InterpretationException {
                return new Nop(chapter, Utils.countLeadingSpaces(line));
            }
        };

        public abstract Line parse(String line, int indent, Chapter chapter, Object... additionalParams)
                throws InterpretationException;


        public static LineType getType(String line, State state, boolean escaped, boolean insideStatementBlock)
                throws InterpretationException {
            if(line.isEmpty()) throw new InterpretationException("Empty line");
            if(!escaped  && (line.startsWith("//") || line.startsWith("#")))
                return COMMENT;
            if(!escaped && line.startsWith(":::"))
                return STATEMENT_BLOCK_MARKER;
            if(!escaped && (insideStatementBlock || line.startsWith(":")))
                return STATEMENT;
            if(!escaped && line.startsWith("?"))
                return QUESTION;
            if(!escaped && line.startsWith("*"))
                return ANSWER;
            if(!escaped && line.startsWith("[") && line.contains("]"))
                return INPUT;
            if(line.contains(":") && state.hasVariable(line.split("\\s*:", 2)[0]))
                return SPEECH;
            return NARRATIVE;
        }
    }

    private Question previousQuestion;
    private Line previousLine;
    private Line head;
    private boolean finished = false;
    private boolean insideStatementBlock;
    private int statementBlockIndent;


    public Line parse(List<String> lines, Chapter chapter) throws InterpretationException {
        try {
            for (String line : lines) parse(line, chapter);
            setJumps(chapter);
            return getHead();
        } catch (RuntimeException e) {
            throw new InterpretationException("Unknown interpretation exception", e);
        }
    }

    private String stripComments(String line) {
        int firstOccurence = line.indexOf("//");
        if(firstOccurence < 0) return line;
        if(line.charAt(firstOccurence-1) != '\\') return line.substring(0, firstOccurence).trim();

        int i = firstOccurence+2;
        StringBuilder res = new StringBuilder(line.length());
        for(int s=0; s<i; s++) res.append(line.charAt(s));
        for(;i<line.length()-1;i++) {
            if(line.charAt(i) == '/' && line.charAt(i+1) == '/' && line.charAt(i-1) != '\\')
                break;
            else
                res.append(line.charAt(i));
        }
        for(i = res.length()-1; i>=0 && Character.isWhitespace(i); i--)
            res.deleteCharAt(i);
        return res.toString();
    }

    //this is one mess of a method honestly
    private void parse(String line, Chapter chapter) throws InterpretationException {
        boolean escaped = false;
        int indent = Utils.countLeadingSpaces(line);
        line = line.trim(); // all lines are trimmed at the beginning, and indent is stored separately !!
        if(line.startsWith("\\")) escaped = true;
        if(escaped) line = line.substring(1);

        LineType type;
        if(insideStatementBlock && indent <= statementBlockIndent)
            insideStatementBlock = false;
        type = getType(line, chapter.getState(), escaped, insideStatementBlock);

        if(type == COMMENT) return;
        if(type == STATEMENT_BLOCK_MARKER) {
            insideStatementBlock = true;
            statementBlockIndent = indent;
            return;
        }

        int commIndex = line.indexOf("//");
        if(commIndex >= 0) line = stripComments(line);
        Line current;
        if(type == ANSWER) {
            current = type.parse(line, indent, chapter, previousQuestion);
        } else {
            current = type.parse(line, indent, chapter);
        }
        if(head == null) head = current;
        if(previousLine != null && previousLine != current) previousLine.setNextLine(current);
        //previousLine == current iff current instanceof Question && type == ANSWER, because ANSWER isn't a Line
        //todo improve parsing (move Question#nextLine setting to jumps)
        previousLine = current;
        if(current instanceof Question) previousQuestion = (Question) current;
    }

    public Line getHead() {
        if(!finished) System.err.println("warning: requested head before parsing is finished");
        return head;
    }

    private void setJumps(Chapter chapter) throws InterpretationException {
        Line curr = head;
        Deque<Utils.Pair<Integer, IfStatement>> indentStack = new LinkedList<>();
        while(curr != null) {
            Line next = curr.getNextLine();
            if(curr instanceof GotoStatement && !((GotoStatement)curr).hasSetJump()) {
                ((GotoStatement)curr).setJump(chapter);
            } //addressing only forward jumps here

            while(!indentStack.isEmpty() && curr.getIndent() <= indentStack.peekLast().a) {
                indentStack.pollLast().b.setNextIfFalse(curr);
            }
            if(curr instanceof IfStatement) {
                indentStack.push(new Utils.Pair<>(curr.getIndent(), (IfStatement) curr));
            }

            curr = next;
        }
        finished = true;
    }
}

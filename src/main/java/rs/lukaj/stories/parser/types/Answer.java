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

package rs.lukaj.stories.parser.types;

import rs.lukaj.stories.exceptions.InterpretationException;
import rs.lukaj.stories.parser.Expressions;
import rs.lukaj.stories.runtime.Chapter;
import rs.lukaj.stories.runtime.State;

/**
 * Created by luka on 3.6.17..
 */
public class Answer implements AnswerLike {
    protected String variable;
    protected String text;

    public Answer(Chapter chapter, String variable, String text) throws InterpretationException {
        chapter.getState().declareVariable(variable);
        this.variable = variable;
        this.text = text;
    }

    public String getVariable() {
        return variable;
    }

    @Override
    public Object getContent(State state) {
        return getText(state);
    }

    public String getText(State state) {
        return Expressions.substituteVariables(text, state);
    }
}

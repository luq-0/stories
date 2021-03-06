/*
  Stories - an interactive storytelling language
  Copyright (C) 2017-2018 Luka Jovičić

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

package rs.lukaj.stories.parser.lines;

import rs.lukaj.stories.exceptions.InterpretationException;
import rs.lukaj.stories.runtime.Chapter;

/**
 * Created by luka on 4.6.17..
 */
public class TimedQuestion extends Question {
    public TimedQuestion(Chapter chapter, String variable, String text, String character, double time,
                         int lineNumber, int indent) throws InterpretationException {
        super(chapter, variable, text, character, time, lineNumber, indent);
        if(time <= 0) throw new InterpretationException("Time for question is negative or zero");
    }
}

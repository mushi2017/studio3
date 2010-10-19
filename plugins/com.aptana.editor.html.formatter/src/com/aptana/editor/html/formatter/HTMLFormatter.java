/**
 * This file Copyright (c) 2005-2010 Aptana, Inc. This program is
 * dual-licensed under both the Aptana Public License and the GNU General
 * Public license. You may elect to use one or the other of these licenses.
 * 
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT. Redistribution, except as permitted by whichever of
 * the GPL or APL you select, is prohibited.
 *
 * 1. For the GPL license (GPL), you can redistribute and/or modify this
 * program under the terms of the GNU General Public License,
 * Version 3, as published by the Free Software Foundation.  You should
 * have received a copy of the GNU General Public License, Version 3 along
 * with this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Aptana provides a special exception to allow redistribution of this file
 * with certain other free and open source software ("FOSS") code and certain additional terms
 * pursuant to Section 7 of the GPL. You may view the exception and these
 * terms on the web at http://www.aptana.com/legal/gpl/.
 * 
 * 2. For the Aptana Public License (APL), this program and the
 * accompanying materials are made available under the terms of the APL
 * v1.0 which accompanies this distribution, and is available at
 * http://www.aptana.com/legal/apl/.
 * 
 * You may view the GPL, Aptana's exception and additional terms, and the
 * APL in the file titled license.html at the root of the corresponding
 * plugin containing this source file.
 * 
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.editor.html.formatter;

import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import com.aptana.editor.html.parsing.HTMLParseState;
import com.aptana.formatter.AbstractScriptFormatter;
import com.aptana.formatter.FormatterDocument;
import com.aptana.formatter.FormatterIndentDetector;
import com.aptana.formatter.FormatterWriter;
import com.aptana.formatter.IFormatterContext;
import com.aptana.formatter.IScriptFormatter;
import com.aptana.formatter.epl.FormatterPlugin;
import com.aptana.formatter.nodes.IFormatterContainerNode;
import com.aptana.formatter.preferences.IPreferenceDelegate;
import com.aptana.formatter.ui.CodeFormatterConstants;
import com.aptana.formatter.ui.FormatterException;
import com.aptana.formatter.ui.FormatterMessages;
import com.aptana.formatter.util.DumpContentException;
import com.aptana.parsing.IParseState;
import com.aptana.parsing.IParser;
import com.aptana.parsing.ast.IParseNode;

/**
 * HTML code formatter.
 * 
 * @author Shalom Gibly <sgibly@aptana.com>
 */
public class HTMLFormatter extends AbstractScriptFormatter implements IScriptFormatter
{

	/**
	 * Blank lines constants
	 */
	protected static final String[] BLANK_LINES = { HTMLFormatterConstants.LINES_AFTER_ELEMENTS,
			HTMLFormatterConstants.LINES_BEFORE_NON_HTML_ELEMENTS, HTMLFormatterConstants.LINES_AFTER_NON_HTML_ELEMENTS };

	private String lineSeparator;

	/**
	 * Constructor.
	 * 
	 * @param preferences
	 */
	protected HTMLFormatter(String lineSeparator, Map<String, ? extends Object> preferences, String mainContentType)
	{
		super(preferences, mainContentType);
		this.lineSeparator = lineSeparator;
	}

	/**
	 * Detects the indentation level.
	 */
	public int detectIndentationLevel(IDocument document, int offset)
	{
		IParser parser = getParser();
		IParseState parseState = new HTMLParseState();
		String source = document.get();
		parseState.setEditState(source, null, 0, 0);
		int indent = 0;
		try
		{
			IParseNode parseResult = parser.parse(parseState);
			if (parseResult != null)
			{
				final HTMLFormatterNodeBuilder builder = new HTMLFormatterNodeBuilder();
				final FormatterDocument formatterDocument = createFormatterDocument(source);
				IFormatterContainerNode root = builder.build(parseResult, formatterDocument);
				new HTMLFormatterNodeRewriter(parseResult, formatterDocument).rewrite(root);
				IFormatterContext context = new HTMLFormatterContext(0);
				FormatterIndentDetector detector = new FormatterIndentDetector(offset);
				try
				{
					root.accept(context, detector);
					return detector.getLevel();
				}
				catch (Exception e)
				{
					// ignore
				}
			}
		}
		catch (Throwable t)
		{
			indent = alternativeDetectIndentationLevel(document, offset);
		}
		return indent;
	}

	/**
	 * Returns the indentation level by looking at the previous line and the formatter settings for the tabs and spaces.
	 * This is an alternative way that is invoked if the parser fails to parse the HTML content.
	 * 
	 * @param document
	 * @param offset
	 * @return
	 */
	private int alternativeDetectIndentationLevel(IDocument document, int offset)
	{
		try
		{
			int lineNumber = document.getLineOfOffset(offset);
			if (lineNumber > 0)
			{
				IRegion previousLineRegion = document.getLineInformation(lineNumber - 1);
				String text = document.get(previousLineRegion.getOffset(), previousLineRegion.getLength());
				// grab the empty string at the beginning of the text.
				int spaceChars = 0;
				int tabChars = 0;
				for (int i = 0; i < text.length(); i++)
				{
					char c = text.charAt(i);
					if (!Character.isWhitespace(c))
					{
						break;
					}
					if (c == '\n' || c == '\r')
					{
						// ignore it
						continue;
					}
					if (c == ' ')
					{
						spaceChars++;
					}
					else if (c == '\t')
					{
						tabChars++;
					}
				}
				String indentType = getIndentType();
				int indentSize = getIndentSize();
				int tabSize = getTabSize();
				if (CodeFormatterConstants.TAB.equals(indentType))
				{
					// treat the whitespace-chars as tabs
					return (spaceChars / tabSize) + tabChars + 1;
				}
				else if (CodeFormatterConstants.SPACE.equals(indentType))
				{
					// treat the tabs as spaces
					return (spaceChars + (tabSize * tabChars)) / indentSize + 1;
				}
				else
				{
					// it's Mixed
					return (spaceChars + tabChars) / indentSize + 1;
				}

			}
		}
		catch (BadLocationException e)
		{
			FormatterPlugin.logError(e);
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.formatter.ui.IScriptFormatter#format(java.lang.String, int, int, int)
	 */
	public TextEdit format(String source, int offset, int length, int indentationLevel) throws FormatterException
	{
		String input = source.substring(offset, offset + length);
		IParser parser = getParser();
		IParseState parseState = new HTMLParseState();
		parseState.setEditState(input, null, 0, 0);
		try
		{
			IParseNode parseResult = parser.parse(parseState);
			if (parseResult != null)
			{
				final String output = format(input, parseResult, indentationLevel);
				if (output != null)
				{
					if (!input.equals(output))
					{
						if (equalsIgnoreWhitespaces(input, output))
						{
							return new ReplaceEdit(offset, length, output);
						}
						else
						{
							FormatterPlugin.log(new Status(IStatus.ERROR, HTMLFormatterPlugin.PLUGIN_ID, IStatus.OK,
									FormatterMessages.Formatter_formatterError, new DumpContentException(input
											+ "\n=========================\n" + output))); //$NON-NLS-1$
						}
					}
					else
					{
						return new MultiTextEdit(); // NOP
					}
				}
			}
		}
		catch (Exception e)
		{
			FormatterPlugin.logError(e);
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.formatter.ui.IScriptFormatter#getIndentSize()
	 */
	public int getIndentSize()
	{
		return getInt(HTMLFormatterConstants.FORMATTER_INDENTATION_SIZE);
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.formatter.ui.IScriptFormatter#getIndentType()
	 */
	public String getIndentType()
	{
		return getString(HTMLFormatterConstants.FORMATTER_TAB_CHAR);
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.formatter.ui.IScriptFormatter#getTabSize()
	 */
	public int getTabSize()
	{
		return getInt(HTMLFormatterConstants.FORMATTER_TAB_SIZE);
	}

	/**
	 * Do the actual formatting of the HTML.
	 * 
	 * @param input
	 *            The String input
	 * @param parseResult
	 *            An HTML parser result - {@link IParseNode}
	 * @param indentationLevel
	 *            The indentation level to start from
	 * @return A formatted string
	 */
	private String format(String input, IParseNode parseResult, int indentationLevel)
	{
		final HTMLFormatterNodeBuilder builder = new HTMLFormatterNodeBuilder();
		final FormatterDocument document = createFormatterDocument(input);
		IFormatterContainerNode root = builder.build(parseResult, document);
		new HTMLFormatterNodeRewriter(parseResult, document).rewrite(root);
		IFormatterContext context = new HTMLFormatterContext(indentationLevel);
		FormatterWriter writer = new FormatterWriter(document, lineSeparator, createIndentGenerator());
		writer.setWrapLength(getInt(HTMLFormatterConstants.WRAP_COMMENTS_LENGTH));
		writer.setLinesPreserve(getInt(HTMLFormatterConstants.PRESERVED_LINES));
		try
		{
			root.accept(context, writer);
			writer.flush(context);
			return writer.getOutput();
		}
		catch (Exception e)
		{
			FormatterPlugin.logError(e);
			return null;
		}
	}

	private FormatterDocument createFormatterDocument(String input)
	{
		FormatterDocument document = new FormatterDocument(input);
		document.setInt(HTMLFormatterConstants.FORMATTER_TAB_SIZE, getInt(HTMLFormatterConstants.FORMATTER_TAB_SIZE));
		document.setBoolean(HTMLFormatterConstants.WRAP_COMMENTS, getBoolean(HTMLFormatterConstants.WRAP_COMMENTS));
		document.setSet(HTMLFormatterConstants.INDENT_EXCLUDED_TAGS, getSet(
				HTMLFormatterConstants.INDENT_EXCLUDED_TAGS, IPreferenceDelegate.PREFERECE_DELIMITER));
		document.setSet(HTMLFormatterConstants.NEW_LINES_EXCLUDED_TAGS, getSet(
				HTMLFormatterConstants.NEW_LINES_EXCLUDED_TAGS, IPreferenceDelegate.PREFERECE_DELIMITER));
		for (int i = 0; i < BLANK_LINES.length; i++)
		{
			document.setInt(BLANK_LINES[i], getInt(BLANK_LINES[i]));
		}
		return document;
	}

}

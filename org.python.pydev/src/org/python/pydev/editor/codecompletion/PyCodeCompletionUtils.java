package org.python.pydev.editor.codecompletion;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.python.pydev.core.ICodeCompletionASTManager.ImportInfo;
import org.python.pydev.core.docutils.DocUtils;

public class PyCodeCompletionUtils {

    public static ImportInfo getImportsTipperStr(IDocument doc, int documentOffset) {
        IRegion region;
        try {
            region = doc.getLineInformationOfOffset(documentOffset);
            String trimmedLine = doc.get(region.getOffset(), documentOffset - region.getOffset());
            trimmedLine = trimmedLine.trim();
            return PyCodeCompletionUtils.getImportsTipperStr(trimmedLine, true);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param doc
     * @param documentOffset
     * @return the import info or null if none is available
     */
    public static ImportInfo getImportsTipperStr(String trimmedLine, boolean returnEvenEmpty) {
        String importMsg = "";

        if (!trimmedLine.startsWith("from") && !trimmedLine.startsWith("import")) {
            return new ImportInfo("", false); // it is not an import
        }

        int fromIndex = trimmedLine.indexOf("from");
        int importIndex = trimmedLine.indexOf("import");
        boolean foundImportOnArray = false;

        // check if we have a from or an import.
        if (fromIndex != -1 || importIndex != -1) {
            trimmedLine = trimmedLine.replaceAll("#.*", ""); // remove
                                                                // comments
            String[] strings = trimmedLine.split(" ");

            if (fromIndex != -1 && importIndex == -1) {
                if (strings.length > 2) {
                    // user has spaces as in 'from xxx uuu'
                    return new ImportInfo("", foundImportOnArray);
                }
            }

            for (int i = 0; i < strings.length; i++) {
                if (strings[i].equals("import")) {
                    foundImportOnArray = true;
                }

                if (strings[i].equals("from") == false && strings[i].equals("import") == false) {
                    if (importMsg.length() != 0) {
                        importMsg += '.';
                    }
                    importMsg += strings[i];
                }
                // now, if we have a from xxx import something, we'll always
                // want to return only the xxx
                if (fromIndex != -1 && importIndex == -1 && (foundImportOnArray || i == strings.length - 1)) {
                    if (importMsg.length() == 0) {
                        return PyCodeCompletionUtils.doExistingOrEmptyReturn(returnEvenEmpty, importMsg, foundImportOnArray);
                    }
                    if (importMsg.startsWith(".")) {
                        return new ImportInfo(importMsg, foundImportOnArray);
                    }
                    if (importMsg.indexOf(".") == -1) {
                        return PyCodeCompletionUtils.doExistingOrEmptyReturn(returnEvenEmpty, importMsg, foundImportOnArray);
                    }
                    return new ImportInfo(importMsg.substring(0, importMsg.lastIndexOf(".") + 1), foundImportOnArray);

                }
            }

            if (fromIndex != -1 && importIndex != -1) {
                if (strings.length == 3) {
                    importMsg += '.';
                }
            }
        } else {
            return new ImportInfo("", foundImportOnArray);
        }
        if (importMsg.indexOf(".") == -1) {
            // we have only import fff or from iii (so, we're going for all
            // imports).
            return PyCodeCompletionUtils.doExistingOrEmptyReturn(returnEvenEmpty, importMsg, foundImportOnArray);
        }

        if (fromIndex == -1 && importMsg.indexOf(',') != -1) {
            // we have something like import xxx, yyy, ...
            importMsg = importMsg.substring(importMsg.lastIndexOf(',') + 1, importMsg.length());
            if (importMsg.startsWith(".")) {
                importMsg = importMsg.substring(1);
            }

            int j = importMsg.lastIndexOf('.');
            if (j != -1) {
                importMsg = importMsg.substring(0, j);
                return new ImportInfo(importMsg, foundImportOnArray);
            } else {
                return PyCodeCompletionUtils.doExistingOrEmptyReturn(returnEvenEmpty, importMsg, foundImportOnArray);
            }

        } else {
            // now, we may still have something like 'unittest.test,' or
            // 'unittest.test.,'
            // so, we have to remove this comma (s).
            int i;
            boolean removed = false;
            while ((i = importMsg.indexOf(',')) != -1) {
                if (importMsg.charAt(i - 1) == '.') {
                    int j = importMsg.lastIndexOf('.');
                    importMsg = importMsg.substring(0, j);
                }

                int j = importMsg.lastIndexOf('.');
                importMsg = importMsg.substring(0, j);
                removed = true;
            }

            // if it is something like aaa.sss.bb : removes the bb because it is
            // the qualifier
            // if it is something like aaa.sss. : removes only the last point
            if (!removed && importMsg.length() > 0 && importMsg.indexOf('.') != -1) {
                importMsg = importMsg.substring(0, importMsg.lastIndexOf('.'));
            }

            return new ImportInfo(importMsg, foundImportOnArray);
        }
    }

    static ImportInfo doExistingOrEmptyReturn(boolean returnEvenEmpty, String importMsg, boolean foundImportOnArray) {
        if (returnEvenEmpty || importMsg.trim().length() > 0) {
            return new ImportInfo(" ", foundImportOnArray);
        } else {
            return new ImportInfo("", foundImportOnArray);
        }
    }

    /**
     * Return a document to parse, using some heuristics to make it parseable.
     * 
     * @param doc
     * @param documentOffset
     * @return
     */
    public static String getDocToParse(IDocument doc, int documentOffset) {
        int lineOfOffset = -1;
        try {
            lineOfOffset = doc.getLineOfOffset(documentOffset);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        if (lineOfOffset != -1) {
            String docToParseFromLine = DocUtils.getDocToParseFromLine(doc, lineOfOffset);
            if (docToParseFromLine != null)
                return docToParseFromLine;
            // return "\n"+docToParseFromLine;
            else
                return "";
        } else {
            return "";
        }
    }

    /**
     * Filters the python completions so that only the completions we care about are shown (given the qualifier) 
     * @param pythonAndTemplateProposals the completions to sort / filter
     * @param qualifier the qualifier we care about
     * @param onlyForCalltips if we should filter having in mind that we're going to show it for a calltip
     * @return the completions to show to the user
     */
    @SuppressWarnings("unchecked")
    public static ICompletionProposal[] onlyValidSorted(List pythonAndTemplateProposals, String qualifier, boolean onlyForCalltips) {
        //FOURTH: Now, we have all the proposals, only thing is deciding wich ones are valid (depending on
        //qualifier) and sorting them correctly.
        Collection returnProposals = new HashSet();
        String lowerCaseQualifier = qualifier.toLowerCase();
        
        for (Iterator iter = pythonAndTemplateProposals.iterator(); iter.hasNext();) {
            Object o = iter.next();
            if (o instanceof ICompletionProposal) {
                ICompletionProposal proposal = (ICompletionProposal) o;
            
                String displayString = proposal.getDisplayString();
                if(onlyForCalltips){
                    if (displayString.equals(qualifier)){
                        returnProposals.add(proposal);
                        
                    }else if (displayString.length() > qualifier.length() && displayString.startsWith(qualifier)){
                        if(displayString.charAt(qualifier.length()) == '('){
                            returnProposals.add(proposal);
                            
                        }
                    }
                }else if (displayString.toLowerCase().startsWith(lowerCaseQualifier)) {
                    returnProposals.add(proposal);
                }
            }else{
                throw new RuntimeException("Error: expected instanceof ICompletionProposal and received: "+o.getClass().getName());
            }
        }
    
        ICompletionProposal[] proposals = new ICompletionProposal[returnProposals.size()];
    
        // and fill with list elements
        returnProposals.toArray(proposals);
    
        Arrays.sort(proposals, IPyCodeCompletion.PROPOSAL_COMPARATOR);
        return proposals;
    }

}

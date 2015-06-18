package folioxml.export.plugins;

import folioxml.config.*;
import folioxml.core.InvalidMarkupException;
import folioxml.core.TokenUtils;
import folioxml.css.StylesheetBuilder;
import folioxml.export.FileNode;
import folioxml.export.InfobaseSetPlugin;
import folioxml.slx.ISlxTokenReader;
import folioxml.slx.SlxRecord;
import folioxml.xml.*;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;


public class ExportXmlFile implements InfobaseSetPlugin {

    public ExportXmlFile(boolean indentXml){
        this.indentXml = indentXml;
    }
    private boolean indentXml = true;

    private Deque<FileNode> openFileNodes = null;
    protected OutputStreamWriter out;
    private int indentLevel = 0;
    private String indentString = "  ";

    private boolean  skipNormalRecords = true;

    @Override
    public void beginInfobaseSet(InfobaseSet set, ExportLocations export) throws IOException {
        out  = new OutputStreamWriter(new FileOutputStream(export.getLocalPath(set.getId() + ".xml", AssetType.Xml, FolderCreation.CreateParents).toString()), "UTF8");

        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        openElement("infobases");

        skipNormalRecords = set.getBool("skip_normal_records");

    }

    boolean infobaseTagOpened = false;
    InfobaseConfig current;
    @Override
    public void beginInfobase(InfobaseConfig infobase) throws IOException {
        current= infobase;
        openFileNodes = new ArrayDeque<FileNode>();
    }

    @Override
    public ISlxTokenReader wrapSlxReader(ISlxTokenReader reader) {
        return reader;
    }

    @Override
    public void onSlxRecordParsed(SlxRecord clean_slx) throws InvalidMarkupException, IOException {

    }

    @Override
    public void onRecordTransformed(XmlRecord xr, SlxRecord dirty_slx) throws InvalidMarkupException, IOException {

    }

    @Override
    public FileNode assignFileNode(XmlRecord xr, SlxRecord dirty_slx) throws InvalidMarkupException, IOException {
        return null;
    }

    @Override
    public void onRecordComplete(XmlRecord xr, FileNode file) throws InvalidMarkupException, IOException {
        if (xr.isRootRecord() == infobaseTagOpened)
            throw new InvalidMarkupException("The first record of every infobase should be the root record");

        if (!infobaseTagOpened){
            String author = new NodeList(xr).search(new NodeFilter("infobase-meta","type","author")).getTextContents().trim();
            String title = new NodeList(xr).search(new NodeFilter("infobase-meta","type","title")).first().get("content").trim();

            Node n = new Node("<infobase></infobase>");
            n.set("author", author);
            n.set("title", title);
            n.set("name", current.getId());
            n.set("levelDefOrder", xr.get("levelDefOrder"));
            n.set("levels", xr.get("levels"));
            openElement(n);

            infobaseTagOpened = true;
        }

        else{

            FileNode common = openFileNodes.isEmpty() ? null : getCommonAncestor(file, openFileNodes.peek(), true);
            closeAllUntil(common);
            if (common != file){
                openChildren();
                openFile(file); //We can't descend more than one node at a time; every file node must have 1 or more records.
            }
            openBody();
            if (skipNormalRecords && !xr.isLevelRecord()) return;
            if (indentXml)
                out.write(new XmlFormatter(indentLevel, indentString).format(xr));
            else
                out.write(xr.toXmlString(false));
        }
    }




    public Deque<FileNode> getAncestors(FileNode n, boolean includeSelf){
        Deque<FileNode> parents = new ArrayDeque<FileNode>();

        FileNode current = n;
        if (includeSelf) parents.add(n);
        while (current.getParent() != null){
            parents.addLast(current.getParent());
            current = current.getParent();
        }
        return parents;
    }

    public FileNode getCommonAncestor(FileNode a, FileNode b, boolean includeSelves){
        Deque<FileNode> parents = getAncestors(a, includeSelves);
        Deque<FileNode> otherParents = getAncestors(b, includeSelves);
        FileNode common = null;
        while (!parents.isEmpty() && !otherParents.isEmpty()){
            FileNode c = parents.removeLast();
            if (c == otherParents.removeLast()) common = c;
            else break;
        }
        return common;
    }

    private boolean getBool(FileNode n, String key, boolean defaultValue){
        Object o = n.getBag().get(key);
        return o == null ? defaultValue : (Boolean)o;
    }

    private void setBool(FileNode n, String key, boolean value){
        n.getBag().put(key,value);
    }

    private void openFile(FileNode n) throws IOException, InvalidMarkupException {
        openFileNodes.push(n);
        Node xn = new Node("<file></file>");
        xn.getAttributes().putAll(n.getAttributes());
        openElement(xn);

    }

    private void openBody() throws IOException {
        FileNode top = openFileNodes.peek();
        if (!getBool(top, "bodyOpen", false)){
            openElement("body");
            setBool(top,"bodyOpen", true);
        }
    }

    private void openChildren() throws IOException {
        FileNode top = openFileNodes.peek();
        if (top == null) return;
        if (getBool(top, "bodyOpen", false)){
            closeElement("body");
            setBool(top,"bodyOpen", false);
        }
        if (!getBool(top, "childrenOpen", false)){
            openElement("children");
            setBool(top,"childrenOpen", true);
        }
        //Close body tag if open, open children tag.
    }
    private void closeAllUntil(FileNode until) throws IOException {
        while (!openFileNodes.isEmpty()){
            if (openFileNodes.peek() == until) return;
            FileNode top = openFileNodes.removeFirst();
            if (getBool(top, "bodyOpen", false)){
                closeElement("body");
                setBool(top,"bodyOpen", false);
            }
            if (getBool(top, "childrenOpen", false)){
                closeElement("children");
                setBool(top,"childrenOpen", false);
            }
            closeElement("file");
        }
    }

    private void writeIndent() throws IOException {
        for (int i = 0; i < indentLevel; i++){
            out.append(indentString);
        }
    }
    private void openElement(String elementName) throws IOException {
        writeIndent();
        out.append("<");
        out.append(elementName);
        out.append(">\n");
        indentLevel++;
    }
    private void openElement(Node element) throws IOException {
        StringBuilder sb = new StringBuilder();
        writeIndent();
        element.writeTokenTo(sb);
        out.append(sb);
        out.append("\n");
        indentLevel++;
    }
    private void closeElement(String elementName) throws IOException {
        indentLevel--;
        writeIndent();
        out.append("</");
        out.append(elementName);
        out.append(">\n");
    }

    @Override
    public void endInfobase(InfobaseConfig infobase) throws IOException {
        if (infobaseTagOpened) {
            closeAllUntil(null);

            infobaseTagOpened = false;
        }
    }

    @Override
    public void endInfobaseSet(InfobaseSet set) throws IOException {
        closeElement("infobases");
        out.close();
    }
}

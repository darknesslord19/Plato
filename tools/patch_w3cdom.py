import os

with open('w3cdom_1_23_2.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Patch contextNode
old_cn = """    public Node contextNode(Document wDoc) {
        return (Node) wDoc.getUserData(ContextNodeProperty);
    }"""
new_cn = """    public Node contextNode(Document wDoc) {
        try {
            return (Node) wDoc.getUserData(ContextNodeProperty);
        } catch (Throwable ignored) {
            return null;
        }
    }"""
assert old_cn in content, 'old_cn not found'
content = content.replace(old_cn, new_cn)

# 2. Patch sourceNodes
old_sn = """        for (int i = 0; i < nodeList.getLength(); i++) {
            org.w3c.dom.Node node = nodeList.item(i);
            Object source = node.getUserData(W3CDom.SourceProperty);
            if (nodeType.isInstance(source))
                nodes.add(nodeType.cast(source));
        }"""
new_sn = """        for (int i = 0; i < nodeList.getLength(); i++) {
            org.w3c.dom.Node node = nodeList.item(i);
            Object source = null;
            try {
                source = node.getUserData(W3CDom.SourceProperty);
            } catch (Throwable ignored) {
            }
            if (nodeType.isInstance(source))
                nodes.add(nodeType.cast(source));
        }"""
assert old_sn in content, 'old_sn not found'
content = content.replace(old_sn, new_sn)

# 3. Patch W3CBuilder constructor
old_wb = """        public W3CBuilder(Document doc) {
            this.doc = doc;
            dest = doc;
            sourceNamespaces.put("xml", Parser.NamespaceXml);
            outputNamespaces.put("xml", Parser.NamespaceXml);
            contextElement = (org.jsoup.nodes.Element) doc.getUserData(ContextProperty); // Track the context jsoup Element, so we can save the corresponding w3c element
        }"""
new_wb = """        public W3CBuilder(Document doc) {
            this.doc = doc;
            dest = doc;
            sourceNamespaces.put("xml", Parser.NamespaceXml);
            outputNamespaces.put("xml", Parser.NamespaceXml);
            Object ctx = null;
            try {
                ctx = doc.getUserData(ContextProperty);
            } catch (Throwable ignored) {
            }
            contextElement = (org.jsoup.nodes.Element) ctx;
        }"""
assert old_wb in content, 'old_wb not found'
content = content.replace(old_wb, new_wb)

# 4. Patch appendDocumentType
old_adt = """        private void appendDocumentType(org.jsoup.nodes.DocumentType source) {
            try {
                DocumentType type = doc.getImplementation().createDocumentType(source.name(), source.publicId(), source.systemId());
                append(type, source);
            } catch (DOMException ignored) {
                // invalid / empty doctype dropped
            }
        }"""
new_adt = """        private void appendDocumentType(org.jsoup.nodes.DocumentType source) {
            try {
                DocumentType type = doc.getImplementation().createDocumentType(source.name(), source.publicId(), source.systemId());
                append(type, source);
            } catch (Throwable ignored) {
                // invalid / empty doctype dropped or Harmony DOM DocumentType limitations
            }
        }"""
assert old_adt in content, 'old_adt not found'
content = content.replace(old_adt, new_adt)

# 5. Patch append
old_app = """        private void append(Node append, org.jsoup.nodes.Node source) {
            append.setUserData(SourceProperty, source, null);
            dest.appendChild(append);
        }"""
new_app = """        private void append(Node append, org.jsoup.nodes.Node source) {
            try {
                append.setUserData(SourceProperty, source, null);
            } catch (Throwable ignored) {
            }
            try {
                dest.appendChild(append);
            } catch (DOMException ignored) {
            }
        }"""
assert old_app in content, 'old_app not found'
content = content.replace(old_app, new_app)

# 6. Patch tail
old_tail = """        @Override
        public void tail(org.jsoup.nodes.Node source, int depth) {
            // head may emit an unrepresentable element as text without descending, so only ascend from its matching output element
            if (source instanceof org.jsoup.nodes.Element && dest.getUserData(SourceProperty) == source &&
                dest.getParentNode() != null) {
                dest = dest.getParentNode(); // undescend
            }"""
new_tail = """        @Override
        public void tail(org.jsoup.nodes.Node source, int depth) {
            // head may emit an unrepresentable element as text without descending, so only ascend from its matching output element
            Object destSource = null;
            try {
                destSource = dest.getUserData(SourceProperty);
            } catch (Throwable ignored) {
            }
            if (source instanceof org.jsoup.nodes.Element && destSource == source &&
                dest.getParentNode() != null) {
                dest = dest.getParentNode(); // undescend
            }"""
assert old_tail in content, 'old_tail not found'
content = content.replace(old_tail, new_tail)

target_dir = os.path.join('app', 'src', 'main', 'java', 'org', 'jsoup', 'helper')
os.makedirs(target_dir, exist_ok=True)
target_path = os.path.join(target_dir, 'W3CDom.java')
with open(target_path, 'w', encoding='utf-8') as f:
    f.write(content)

print('Successfully patched and wrote to:', target_path)

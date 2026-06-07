package com.communi.suggestu.formatum.adhibe.checkstyle.config;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CheckstyleConfigParser {
    public CheckstyleModuleSpec parse(Path xmlFile) {
        try {
            String xml = Files.readString(xmlFile);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            XMLReader reader = factory.newSAXParser().getXMLReader();
            ModuleBuildingHandler handler = new ModuleBuildingHandler();
            reader.setContentHandler(handler);
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            reader.parse(new InputSource(new StringReader(xml)));
            return handler.root();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse checkstyle file: " + xmlFile, e);
        }
    }

    private static final class ModuleBuildingHandler extends DefaultHandler {
        private final ArrayDeque<MutableModule> stack = new ArrayDeque<>();
        private Locator locator;
        private CheckstyleModuleSpec root;

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("module".equals(qName)) {
                String name = attributes.getValue("name");
                MutableModule mutable = new MutableModule(name, line(), column());
                if (!stack.isEmpty()) {
                    stack.peek().children.add(mutable);
                }
                stack.push(mutable);
                return;
            }

            if ("property".equals(qName) && !stack.isEmpty()) {
                MutableModule current = stack.peek();
                String name = attributes.getValue("name");
                String value = attributes.getValue("value");
                current.properties.put(name, value);
                current.propertySpecs.add(new CheckstylePropertySpec(name, value, line(), column()));
                return;
            }

            if ("message".equals(qName) && !stack.isEmpty()) {
                stack.peek().messages.add(new CheckstyleMessageSpec(
                        attributes.getValue("key"),
                        attributes.getValue("value"),
                        line(),
                        column()
                ));
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (!"module".equals(qName)) {
                return;
            }
            MutableModule completed = stack.pop();
            if (stack.isEmpty()) {
                root = completed.toImmutable(completed.name + "[0]");
            }
        }

        private int line() {
            return locator == null ? -1 : locator.getLineNumber();
        }

        private int column() {
            return locator == null ? -1 : locator.getColumnNumber();
        }

        public CheckstyleModuleSpec root() {
            if (root == null) {
                throw new IllegalStateException("No root module parsed");
            }
            return root;
        }
    }

    private static final class MutableModule {
        private final String name;
        private final int line;
        private final int column;
        private final LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        private final List<CheckstylePropertySpec> propertySpecs = new ArrayList<>();
        private final List<CheckstyleMessageSpec> messages = new ArrayList<>();
        private final List<MutableModule> children = new ArrayList<>();

        private MutableModule(String name, int line, int column) {
            this.name = name;
            this.line = line;
            this.column = column;
        }

        private CheckstyleModuleSpec toImmutable(String path) {
            Map<String, Integer> moduleNameCounts = new LinkedHashMap<>();
            List<CheckstyleModuleSpec> builtChildren = new ArrayList<>(children.size());
            for (MutableModule child : children) {
                int count = moduleNameCounts.getOrDefault(child.name, 0);
                moduleNameCounts.put(child.name, count + 1);
                builtChildren.add(child.toImmutable(path + "/" + child.name + "[" + count + "]"));
            }
            return new CheckstyleModuleSpec(
                    name,
                    path,
                    line,
                    column,
                    Collections.unmodifiableMap(new LinkedHashMap<>(properties)),
                    List.copyOf(propertySpecs),
                    List.copyOf(messages),
                    List.copyOf(builtChildren)
            );
        }
    }
}



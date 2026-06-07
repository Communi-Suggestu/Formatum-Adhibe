package com.communi.suggestu.formatum.adhibe.checkstyle.classification;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class RuleMatrixGenerator {
    private final CheckstyleRuleClassifier classifier;

    public RuleMatrixGenerator(CheckstyleRuleClassifier classifier) {
        this.classifier = classifier;
    }

    public String generate(CheckstyleModuleSpec root) {
        List<CheckstyleModuleSpec> modules = flatten(root).stream()
                .filter(module -> !"Checker".equals(module.name()))
                .filter(module -> !"TreeWalker".equals(module.name()))
                .toList();

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Rule Matrix\n\n");
        markdown.append("| Module Path | Module | Support | Rationale | Id/Message |\n");
        markdown.append("| --- | --- | --- | --- | --- |\n");

        for (CheckstyleModuleSpec module : modules) {
            RuleClassification classification = classifier.classify(module);
            String idOrMessage = module.id().isPresent() ? module.id().orElse("") : module.message().orElse("");
            markdown.append("| ")
                    .append(escape(module.path()))
                    .append(" | ")
                    .append(escape(module.name()))
                    .append(" | ")
                    .append(classification.support())
                    .append(" | ")
                    .append(escape(classification.rationale()))
                    .append(" | ")
                    .append(escape(idOrMessage))
                    .append(" |\n");
        }

        markdown.append("\n");
        markdown.append("Total rules: ").append(modules.size()).append("\n");
        long unsupported = modules.stream().filter(m -> classifier.classify(m).support() == RuleSupport.UNSUPPORTED).count();
        markdown.append("Unsupported rules: ").append(unsupported).append("\n");
        return markdown.toString();
    }

    public List<CheckstyleModuleSpec> unsupported(CheckstyleModuleSpec root) {
        return flatten(root).stream()
                .filter(module -> module.isLeaf() || "MatchXpath".equals(module.name()))
                .filter(module -> classifier.classify(module).support() == RuleSupport.UNSUPPORTED)
                .collect(Collectors.toList());
    }

    private List<CheckstyleModuleSpec> flatten(CheckstyleModuleSpec root) {
        List<CheckstyleModuleSpec> modules = new ArrayList<>();
        walk(root, modules);
        return modules;
    }

    private void walk(CheckstyleModuleSpec module, List<CheckstyleModuleSpec> modules) {
        modules.add(module);
        for (CheckstyleModuleSpec child : module.children()) {
            walk(child, modules);
        }
    }

    private static String escape(String text) {
        return text.replace("|", "\\|").replace("\n", "<br>");
    }
}



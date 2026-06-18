package de.verdox.pv_miner.frontend.components;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class MarkdownView extends VerticalLayout {
    private Html html;

    public MarkdownView(InputStream markdownInput) {
        setMarkdown(markdownInput);
    }

    public MarkdownView(String markdownText) {
        setMarkdown(markdownText);
    }

    public MarkdownView() {

    }

    public void setMarkdown(InputStream markdownInput) {
        String markdownText = readInputStream(markdownInput);
        setMarkdown(markdownText);
    }

    public void setMarkdown(String markdown) {
        if (this.html != null) {
            remove(html);
        }
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String htmlContent = renderer.render(parser.parse(markdown));
        html = new Html("<div>" + htmlContent + "</div>");
        add(html);
    }

    private String readInputStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            e.printStackTrace();
            return "Could not read markdown";
        }
    }
}

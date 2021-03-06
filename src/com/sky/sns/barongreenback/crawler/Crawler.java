package com.sky.sns.barongreenback.crawler;

import com.sky.sns.barongreenback.shared.RecordDefinition;
import com.googlecode.funclate.Model;
import com.googlecode.lazyrecords.Definition;
import com.googlecode.lazyrecords.Keyword;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.Uri;

import java.util.List;
import java.util.UUID;

import static com.sky.sns.barongreenback.crawler.DuplicateRemover.ignoreAlias;
import static com.sky.sns.barongreenback.shared.RecordDefinition.convert;
import static com.sky.sns.barongreenback.shared.RecordDefinition.toKeywords;
import static com.googlecode.totallylazy.Uri.uri;

public interface Crawler {
    Number crawl(UUID id) throws Exception;

    public class methods {
        public static Sequence<Keyword<?>> keywords(RecordDefinition recordDefinition) {
            return keywords(recordDefinition.definition());
        }

        public static Sequence<Keyword<?>> keywords(Definition definition) {
            return RecordDefinition.allFields(definition).map(ignoreAlias());
        }

        public static Sequence<Keyword<?>> allKeywords(Sequence<Keyword<?>> keywords) {
            return RecordDefinition.allFields(keywords).map(ignoreAlias());
        }

        public static Definition definition(Model crawler, RecordDefinition recordDefinition) {
            return definition(crawler, recordDefinition.definition());
        }

        public static Definition definition(Model crawler, Definition definition) {
            return Definition.constructors.definition(update(crawler), keywords(definition));
        }

        public static RecordDefinition extractRecordDefinition(Model crawler) {
            return convert(crawler.get("record", Model.class));
        }

        public static String update(Model crawler) {
            return crawler.get("update", String.class);
        }

        public static String name(Model crawler) {
            return crawler.get("name", String.class);
        }

        public static Uri from(Model crawler) {
            return uri(crawler.get("from", String.class));
        }

        public static String more(Model crawler) {
            return crawler.get("more", String.class);
        }

        public static Definition sourceDefinition(Model crawler) {
            return extractRecordDefinition(crawler).definition();
        }

        public static Definition destinationDefinition(Model crawler) {
            String name = crawler.get("update", String.class);
            List<Model> keywordsModel = crawler.get("record", Model.class).getValues("keywords", Model.class);
            return Definition.constructors.definition(name, allKeywords(toKeywords(keywordsModel)));
        }
    }
}

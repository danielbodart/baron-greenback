package com.googlecode.barongreenback.search.parser;

import com.googlecode.totallylazy.Predicate;
import com.googlecode.totallylazy.records.Keyword;
import com.googlecode.totallylazy.records.Record;
import org.junit.Test;

import static com.googlecode.totallylazy.records.Keywords.keyword;
import static com.googlecode.totallylazy.records.MapRecord.record;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StandardParserTest {
    @Test
    public void supportsImplicitKeywords() throws Exception{
        Keyword<String> name = keyword("name", String.class);
        PredicateParser predicateParser = new StandardParser(name);
        Predicate<Record> predicate = predicateParser.parse("bob");
        assertThat(predicate.matches(record().set(name, "bob")), is(true));
        assertThat(predicate.matches(record().set(name, "dan")), is(false));
    }

    @Test
    public void supportsExplicitKeywords() throws Exception{
        PredicateParser predicateParser = new StandardParser();
        Predicate<Record> predicate = predicateParser.parse("name:bob");    

        Keyword<String> name = keyword("name", String.class);
        assertThat(predicate.matches(record().set(name, "bob")), is(true));
        assertThat(predicate.matches(record().set(name, "dan")), is(false));
    }

    @Test
    public void supportsMultipleConditions() throws Exception{
        PredicateParser predicateParser = new StandardParser();
        Predicate<Record> predicate = predicateParser.parse("name:bob age:12");

        Keyword<String> name = keyword("name", String.class);
        Keyword<String> age = keyword("age", String.class);
        assertThat(predicate.matches(record().set(name, "bob").set(age, "12")), is(true));
        assertThat(predicate.matches(record().set(name, "bob").set(age, "13")), is(false));
        assertThat(predicate.matches(record().set(name, "dan").set(age, "12")), is(false));
    }

    @Test
    public void supportsNegationWithImplicit() throws Exception{
        PredicateParser predicateParser = new StandardParser(keyword("name", String.class));
        Predicate<Record> predicate = predicateParser.parse("-bob age:12");

        Keyword<String> name = keyword("name", String.class);
        Keyword<String> age = keyword("age", String.class);
        assertThat(predicate.matches(record().set(name, "dan").set(age, "12")), is(true));
        assertThat(predicate.matches(record().set(name, "bob").set(age, "12")), is(false));
        assertThat(predicate.matches(record().set(name, "dan").set(age, "13")), is(false));
    }

    @Test
    public void supportsNegationWithExplicit() throws Exception{
        PredicateParser predicateParser = new StandardParser();
        Predicate<Record> predicate = predicateParser.parse("-name:bob age:12");

        Keyword<String> name = keyword("name", String.class);
        Keyword<String> age = keyword("age", String.class);
        assertThat(predicate.matches(record().set(name, "dan").set(age, "12")), is(true));
        assertThat(predicate.matches(record().set(name, "bob").set(age, "12")), is(false));
        assertThat(predicate.matches(record().set(name, "dan").set(age, "13")), is(false));
    }

    @Test
    public void supportsOrWithImplicit() throws Exception{
        PredicateParser predicateParser = new StandardParser(keyword("name", String.class));
        Predicate<Record> predicate = predicateParser.parse("dan,bob");

        Keyword<String> name = keyword("name", String.class);
        assertThat(predicate.matches(record().set(name, "dan")), is(true));
        assertThat(predicate.matches(record().set(name, "bob")), is(true));
        assertThat(predicate.matches(record().set(name, "mat")), is(false));
    }

    @Test
    public void supportsOrWithExplicit() throws Exception{
        PredicateParser predicateParser = new StandardParser();
        Predicate<Record> predicate = predicateParser.parse("name:dan,bob");

        Keyword<String> name = keyword("name", String.class);
        assertThat(predicate.matches(record().set(name, "dan")), is(true));
        assertThat(predicate.matches(record().set(name, "bob")), is(true));
        assertThat(predicate.matches(record().set(name, "mat")), is(false));
    }

    @Test
    public void supportsQuotedValue() throws Exception{
        PredicateParser predicateParser = new StandardParser();
        Predicate<Record> predicate = predicateParser.parse("name:\"Dan Bod\"");

        Keyword<String> name = keyword("name", String.class);
        assertThat(predicate.matches(record().set(name, "Dan Bod")), is(true));
        assertThat(predicate.matches(record().set(name, "Dan")), is(false));
        assertThat(predicate.matches(record().set(name, "Bod")), is(false));
    }

    @Test
    public void supportsQuotedName() throws Exception{
        PredicateParser predicateParser = new StandardParser();
        Predicate<Record> predicate = predicateParser.parse("\"First Name\":Dan");

        Keyword<String> name = keyword("First Name", String.class);
        assertThat(predicate.matches(record().set(name, "Dan")), is(true));
        assertThat(predicate.matches(record().set(name, "Mat")), is(false));
    }
}

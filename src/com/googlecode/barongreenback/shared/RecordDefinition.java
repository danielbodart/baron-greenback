package com.googlecode.barongreenback.shared;

import com.googlecode.totallylazy.Callable1;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.records.Keyword;
import com.googlecode.totallylazy.records.Keywords;

import static com.googlecode.totallylazy.Predicates.is;
import static com.googlecode.totallylazy.Predicates.where;
import static com.googlecode.totallylazy.Sequences.sequence;
import static com.googlecode.totallylazy.records.Keywords.keyword;
import static com.googlecode.totallylazy.records.Keywords.metadata;

public class RecordDefinition {
    public static final Keyword<RecordDefinition> RECORD_DEFINITION = keyword(RecordDefinition.class.getName(), RecordDefinition.class);
    private final Keyword<Object> recordName;
    private final Sequence<Keyword> fields;

    public RecordDefinition(Keyword<Object> recordName, Sequence<Keyword> fields) {
        this.recordName = recordName;
        this.fields = fields;
    }

    public Keyword<Object> recordName() {
        return recordName;
    }

    public Sequence<Keyword> fields() {
        return fields;
    }





    public static Sequence<Keyword> uniqueFields(RecordDefinition recordDefinition) {
        return allFields(recordDefinition).filter(where(metadata(Keywords.UNIQUE), is(true)));
    }

    public static Sequence<Keyword> allFields(RecordDefinition recordDefinition) {
        return recordDefinition.fields().flatMap(allFields());
    }

    public static Callable1<? super Keyword, Sequence<Keyword>> allFields() {
        return new Callable1<Keyword, Sequence<Keyword>>() {
            public Sequence<Keyword> call(Keyword keyword) throws Exception {
                RecordDefinition recordDefinition = keyword.metadata().get(RecordDefinition.RECORD_DEFINITION);
                if(recordDefinition != null){
                    return sequence(keyword).join(allFields(recordDefinition));
                }
                return sequence(keyword);
            }
        };
    }


}

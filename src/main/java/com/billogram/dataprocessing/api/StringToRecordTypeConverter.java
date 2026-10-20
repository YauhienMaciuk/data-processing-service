package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.domain.RecordType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Lets query parameters use the lowercase JSON values, e.g. {@code ?type=positive}. */
@Component
public class StringToRecordTypeConverter implements Converter<String, RecordType> {

    @Override
    public RecordType convert(String source) {
        return RecordType.fromValue(source);
    }
}

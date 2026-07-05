package com.piggymetrics.statistics.repository.converter;

import com.piggymetrics.statistics.domain.timeseries.DataPointId;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@ReadingConverter
public class DataPointIdReaderConverter implements Converter<Document, DataPointId> {

	@Override
	public DataPointId convert(Document object) {

		Date date = (Date) object.get("date");
		String account = (String) object.get("account");

		return new DataPointId(account, date);
	}
}

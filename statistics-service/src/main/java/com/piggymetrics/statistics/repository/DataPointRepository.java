package com.piggymetrics.statistics.repository;

import com.piggymetrics.statistics.domain.timeseries.DataPoint;
import com.piggymetrics.statistics.domain.timeseries.DataPointId;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataPointRepository extends CrudRepository<DataPoint, DataPointId> {

	// Explicit query on the nested _id field. The derived `findByIdAccount` no
	// longer works in Spring Data Mongo 4 because DataPointId is a converted
	// (simple) type, so the query deriver can't traverse into id.account.
	@Query("{ '_id.account' : ?0 }")
	List<DataPoint> findByIdAccount(String account);

}

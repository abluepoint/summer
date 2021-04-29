package com.abluepoint.summer.data.jpa.template;

import com.abluepoint.summer.data.jpa.JpaSummerException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface JpaSqlTemplate {

//	public <E> List<E> findListBySql(String sql, boolean isNative, Map<String, Object> paramMap, Class<E> resultType);
//	public <E> Page<E> findPageBySql(String sql, String countSql, boolean isNative, Pageable pageable, Map<String, Object> paramMap, Class<E> resultType);
	/**
	 * Retrieve a single row mapped from the statement key.
	 *
	 * @param <T>       the returned object type
	 * @param queryString
	 * @return Mapped object
	 */
	<T> T selectOne(String queryString,Class<T> resultType) throws JpaSummerException;

	/**
	 * Retrieve a single row mapped from the statement key and parameter.
	 *
	 * @param <T>       the returned object type
	 * @param queryString Unique identifier matching the statement to use.
	 * @param parameter A parameter object to pass to the statement.
	 * @return Mapped object
	 */
	<T> T selectOne(String queryString, Map<String,Object> parameter,Class<T> resultType) throws JpaSummerException;

	/**
	 * Retrieve a list of mapped objects from the statement key and parameter.
	 *
	 * @param <E>       the returned list element type
	 * @param queryString Unique identifier matching the statement to use.
	 * @return List of mapped object
	 */
	<E> List<E> selectList(String queryString,Class<E> resultType);

	/**
	 * Retrieve a list of mapped objects from the statement key and parameter.
	 *
	 * @param <E>       the returned list element type
	 * @param queryString Unique identifier matching the statement to use.
	 * @param parameter A parameter object to pass to the statement.
	 * @return List of mapped object
	 */
	<E> List<E> selectList(String queryString, Map<String,Object> parameter,Class<E> resultType);


	/**
	 * Retrieve a list of mapped objects from the statement key and parameter,
	 * within the specified row bounds.
	 *
	 * @param <E>       the returned list element type
	 * @param queryString Unique identifier matching the statement to use.
	 * @param pageable  Bounds to limit object retrieval
	 * @return List of mapped object
	 */
	<E> Page<E> selectPage(String queryString,String countString, Pageable pageable,Class<E> resultType);

	/**
	 * Retrieve a list of mapped objects from the statement key and parameter,
	 * within the specified row bounds.
	 *
	 * @param <E>       the returned list element type
	 * @param queryString Unique identifier matching the statement to use.
	 * @param parameter A parameter object to pass to the statement.
	 * @param pageable  Bounds to limit object retrieval
	 * @return List of mapped object
	 */
	<E> Page<E> selectPage(String queryString,String countString, Map<String,Object> parameter, Pageable pageable,Class<E> resultType);


}

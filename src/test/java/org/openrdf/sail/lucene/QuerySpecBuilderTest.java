/*
 * Copyright Aduna, DFKI and L3S (c) 2007.
 *
 * Licensed under the Aduna BSD-style license.
 */
package org.openrdf.sail.lucene;

import static org.openrdf.model.vocabulary.RDF.TYPE;
import static org.openrdf.sail.lucene.LuceneSailSchema.LUCENE_QUERY;
import static org.openrdf.sail.lucene.LuceneSailSchema.MATCHES;
import static org.openrdf.sail.lucene.LuceneSailSchema.QUERY;
import static org.openrdf.sail.lucene.LuceneSailSchema.SCORE;
import static org.openrdf.sail.lucene.LuceneSailSchema.SNIPPET;

import java.util.Iterator;
import java.util.Set;

import junit.framework.TestCase;

import org.openrdf.model.Literal;
import org.openrdf.query.algebra.QueryModel;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.evaluation.QueryBindingSet;
import org.openrdf.query.parser.serql.SeRQLParser;

public class QuerySpecBuilderTest extends TestCase {

	private QuerySpecBuilder interpreter;
	private SeRQLParser parser;

	@Override
	public void setUp() throws Exception {
		interpreter = new QuerySpecBuilder(true);
		parser = new SeRQLParser();
	}
	
	@Override
	public void tearDown() {
	}
	
	public void testQueryInterpretation() throws Exception {
		StringBuilder buffer = new StringBuilder();
		buffer.append("SELECT Subject, Score, Snippet ");
		buffer.append("FROM {Subject} <" + MATCHES + "> {} ");
		buffer.append("<" + TYPE + "> {<" + LUCENE_QUERY + ">}; ");
		buffer.append("<" + QUERY + "> {\"my Lucene query\"}; ");
		buffer.append("<" + SCORE + "> {Score}; ");
		buffer.append("<" + SNIPPET + "> {Snippet} ");
		QueryModel query = parser.parseQuery(buffer.toString(), null);
		TupleExpr tupleExpr = query.getTupleExpr();
		System.out.print(buffer.toString());
		Set<QuerySpec> queries = interpreter.process(tupleExpr, new QueryBindingSet());
		assertEquals(1, queries.size());
				
		QuerySpec querySpec = queries.iterator().next();
		assertEquals("Subject", querySpec.getMatchesPattern().getSubjectVar().getName());
		assertEquals("my Lucene query", ((Literal) querySpec.getQueryPattern().getObjectVar().getValue()).getLabel());
		assertEquals("Score", querySpec.getScorePattern().getObjectVar().getName());
		assertEquals("Snippet", querySpec.getSnippetPattern().getObjectVar().getName());
		assertEquals(LUCENE_QUERY, querySpec.getTypePattern().getObjectVar().getValue());
		assertEquals("my Lucene query", querySpec.getQueryString());
		assertNull(querySpec.getSubject());
	}
	
	public void testMultipleQueriesInterpretation() throws Exception {
		StringBuilder buffer = new StringBuilder();
		buffer.append("SELECT sub1, score1, snippet1, sub2, score2, snippet2, x, p1, p2 ");
		buffer.append("FROM {sub1} <" + MATCHES + "> {} ");
		buffer.append("<" + TYPE + "> {<" + LUCENE_QUERY + ">}; ");
		buffer.append("<" + QUERY + "> {\"my Lucene query\"}; ");
		buffer.append("<" + SCORE + "> {score1}; ");
		buffer.append("<" + SNIPPET + "> {snippet1}, ");
		buffer.append("{sub2} <" + MATCHES + "> {} ");
		buffer.append("<" + TYPE + "> {<" + LUCENE_QUERY + ">}; ");
		buffer.append("<" + QUERY + "> {\"second lucene query\"}; ");
		buffer.append("<" + SCORE + "> {score2}; ");
		buffer.append("<" + SNIPPET + "> {snippet2}, ");
		// and connect them both via any X in between, just as salt to make the parser do something
		buffer.append("{sub1} p1 {x}, {x} p2 {sub2} ");
		QueryModel query = parser.parseQuery(buffer.toString(), null);
		TupleExpr tupleExpr = query.getTupleExpr();
		
		Set<QuerySpec> queries = interpreter.process(tupleExpr, new QueryBindingSet());
		assertEquals(2, queries.size());
		Iterator<QuerySpec> i = queries.iterator();
		boolean matched1 = false;
		boolean matched2 = false;
		while (i.hasNext())
		{
			QuerySpec querySpec = i.next();
			if ("sub1".equals(querySpec.getMatchesVariableName()))
			{
				// Matched the first
				assertEquals("sub1", querySpec.getMatchesPattern().getSubjectVar().getName());
				assertEquals("my Lucene query", ((Literal) querySpec.getQueryPattern().getObjectVar().getValue()).getLabel());
				assertEquals("score1", querySpec.getScorePattern().getObjectVar().getName());
				assertEquals("snippet1", querySpec.getSnippetPattern().getObjectVar().getName());
				assertEquals(LUCENE_QUERY, querySpec.getTypePattern().getObjectVar().getValue());
				assertEquals("my Lucene query", querySpec.getQueryString());
				assertNull(querySpec.getSubject());
				matched1 = true;
			} else if ("sub2".equals(querySpec.getMatchesVariableName()))
			{
				// and the second
				assertEquals("sub2", querySpec.getMatchesPattern().getSubjectVar().getName());
				assertEquals("second lucene query", ((Literal) querySpec.getQueryPattern().getObjectVar().getValue()).getLabel());
				assertEquals("score2", querySpec.getScorePattern().getObjectVar().getName());
				assertEquals("snippet2", querySpec.getSnippetPattern().getObjectVar().getName());
				assertEquals(LUCENE_QUERY, querySpec.getTypePattern().getObjectVar().getValue());
				assertEquals("second lucene query", querySpec.getQueryString());
				assertNull(querySpec.getSubject());
				matched2 = true;
			} else 
				fail("Found unexpected query spec: "+querySpec.toString());
		}
		if (!matched1)
			fail("did not find query patter sub1");
		if (!matched2)
			fail("did not find query patter sub2");
	}
	
	/**
	 * Incomplete queries should fail, if configured
	 * @throws Exception 
	 */
	public void testIncompleteFail() throws Exception {
		// default works
		String queryString = "SELECT sub1, score1, snippet1 FROM "
			+ "{sub1} <" + MATCHES + "> {} "
			+"<" + TYPE + "> {<" + LUCENE_QUERY + ">}; "
			+"<" + QUERY + "> {\"my Lucene query\"}; "
			+"<" + SCORE + "> {score1}; "
			+"<" + SNIPPET + "> {snippet1}";
		checkQuery(queryString);

		// minimal works
		queryString = "SELECT sub1 FROM "
			+ "{sub1} <" + MATCHES + "> {} "
			+"<" + TYPE + "> {<" + LUCENE_QUERY + ">}; "
			+"<" + QUERY + "> {\"my Lucene query\"} ";
		checkQuery(queryString);

		
		// matches missing
		queryString = "SELECT sub1, score1, snippet1 FROM "
			//+ "{sub1} <" + MATCHES + "> {} "
			+"<" + TYPE + "> {<" + LUCENE_QUERY + ">}; "
			+"<" + QUERY + "> {\"my Lucene query\"}; "
			+"<" + SCORE + "> {score1}; "
			+"<" + SNIPPET + "> {snippet1}";
		try {
			checkQuery(queryString);
			fail("invalid query ignored");
		} catch (Exception x)
		{
			// excellent
		}
		
		// type missing
		queryString = "SELECT sub1, score1, snippet1 FROM "
			+ "{sub1} <" + MATCHES + "> {} "
			//+"<" + TYPE + "> {<" + LUCENE_QUERY + ">}; "
			+"<" + QUERY + "> {\"my Lucene query\"}; "
			+"<" + SCORE + "> {score1}; "
			+"<" + SNIPPET + "> {snippet1}";
		try {
			checkQuery(queryString);
			// excellent
		} catch (Exception x)
		{
			fail("missing type is ok, should not throw an exception");
		}
		
		// query missing
		queryString = "SELECT sub1, score1, snippet1 FROM "
			+ "{sub1} <" + MATCHES + "> {} "
			+"<" + TYPE + "> {<" + LUCENE_QUERY + ">}; "
			//+"<" + QUERY + "> {\"my Lucene query\"}; "
			+"<" + SCORE + "> {score1}; "
			+"<" + SNIPPET + "> {snippet1}";
		try {
			checkQuery(queryString);
			fail("invalid missing query not detected");
		} catch (Exception x)
		{
			// excellent
		}
	}

	/**
	 * Checks if the querystring contains exactly one lucene query
	 * throws exceptions if not or if the query is incomplete
	 * @param queryString
	 */
	private void checkQuery(String queryString) throws Exception {
		QueryModel query = parser.parseQuery(queryString, null);
		TupleExpr tupleExpr = query.getTupleExpr();
		Set<QuerySpec> queries = interpreter.process(tupleExpr, new QueryBindingSet());
		assertEquals("expect one query", 1, queries.size());
	}
}

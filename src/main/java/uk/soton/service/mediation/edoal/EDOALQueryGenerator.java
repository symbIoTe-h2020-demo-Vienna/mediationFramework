/*
 * EDOALQueryGenerator.java
 *
 * Copyright (C) ECS University of Southampton, 2011
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
 */
package uk.soton.service.mediation.edoal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.graph.BlankNodeId;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.syntax.ElementAssign;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;
import org.apache.jena.sparql.syntax.Template;
import org.apache.jena.sparql.util.VarUtils;
import uk.soton.service.mediation.Alignment;
import uk.soton.service.mediation.FunctionalDependency;

/**
 * The EDOALQueryGenerator class generates SPARQL CONSTRUCT queries to import external data based on an EDOAL alignment.
 *
 * @author Gianluca Correndo <gc3@ecs.soton.ac.uk>
 */
public class EDOALQueryGenerator {

    private static Logger log = Logger.getLogger(EDOALQueryGenerator.class
            .getName());

    /**
     * The getExpr method returns the Jena SPARQL Expression from a generic Node
     *
     * @param n the Node instance
     * @return the SPARQL Expression
     *
     */
    private static Expr getExpr(Node n) {
        if (n.isVariable()) {
            return new ExprVar(n);
        }
        if (n.isLiteral() || n.isURI()) {
            return NodeValue.makeNode(n);
        }
        //if (n.isURI())
        //	return new NodeValueNode(n);
        log.log(Level.WARNING, "Node not recognized:" + n.toString());
        return null;
    }

    /**
     * The generateQueriesFromAlignment method returns a list of SPARQL CONSTRUCT queries that implement the input EDOAL
     * alignment.
     *
     * @param patterns the alignment based on rewriting rules
     * @return an array of CONSTRUCT SPARQL queries that implement the mediation
     */
    public static ArrayList<Query> generateQueriesFromAlignment(
            Alignment patterns) {
        try {
            Hashtable<Triple, List<Triple>> pat = patterns.getPatterns();
            Set<Triple> lhss = pat.keySet();
            ArrayList<Query> result = new ArrayList<Query>();
            for (Triple lhs : lhss) {

                List<Triple> rhs = pat.get(lhs);
                Query current = new Query();
                current.setQueryConstructType();
                // Adding the source ontology pattern
                ElementTriplesBlock qp = new ElementTriplesBlock();
                ElementGroup eg = new ElementGroup();
                eg.addElement(qp);
                qp.addTriple(lhs);
                Hashtable<Node, FunctionalDependency> deps = patterns.getFunctionalDependencies().get(lhs);
                //groundVars contains variables whose values will be grounded by the matching process

                //Set<Var> groundVars = qp.varsMentioned();
                Set<Var> groundVars = new HashSet<>();
                if (lhs.getSubject().isVariable()) {
                    groundVars.add((Var) lhs.getSubject());
                }
                if (lhs.getPredicate().isVariable()) {
                    groundVars.add((Var) lhs.getPredicate());
                }
                if (lhs.getObject().isVariable()) {
                    groundVars.add((Var) lhs.getObject());
                }
                Set<Var> freeVars = new HashSet<Var>();
                //Collect all the (free) variables present in the CONSTRUCT clause
                for (Triple t : rhs) {
                    VarUtils.addVarsFromTriple(freeVars, t);
                }
                //Creating LET statements for functional dependencies
                for (Var k : freeVars) {
                    final FunctionalDependency fd = deps.get(k.asNode());
                    if (fd != null) {
                        List<Expr> el = new ArrayList<Expr>();
                        for (Node pa : fd.getParam()) {
                            el.add(getExpr(pa));
                        }
                        eg.addElement(new ElementAssign(getExpr(k).asVar(), fd.getFunc().copy(el)));
                        //The variable k will be assigned by a LET clause
                        groundVars.add(k);
                    } else {
                        log.log(Level.SEVERE, "No fdeps for variable: " + k);
                    }
                }
                freeVars.removeAll(groundVars);
                //freeVars will now contain all the variables that will receive no ground values by the matching process or via LET clauses
                //-------//
                //Add CONSTRUCT target ontology patterns
                BasicPattern pattern = new BasicPattern();
                for (Triple t : rhs) {
                    Node s, p, o;
                    s = t.getSubject();
                    p = t.getPredicate();
                    o = t.getObject();

                    if (s.isVariable()) {
                        if (groundVars.contains(s)) {
                            s = NodeFactory.createVariable(s.getName());
                        } else if (freeVars.contains(s)) {
                            s = NodeFactory.createBlankNode(BlankNodeId.create(s.getName()));
                        }
                    }
                    if (p.isVariable()) {
                        if (groundVars.contains(p)) {
                            p = NodeFactory.createVariable(p.getName());
                        } else if (freeVars.contains(p)) {
                            p = NodeFactory.createBlankNode(BlankNodeId.create(p.getName()));
                        }
                    }
                    if (o.isVariable()) {
                        if (groundVars.contains(o)) {
                            o = NodeFactory.createVariable(o.getName());
                        } else if (freeVars.contains(o)) {
                            o = NodeFactory.createBlankNode(BlankNodeId.create(o.getName()));
                        }
                    }
                    pattern.add(new Triple(s, p, o));
                }
                //Assemble the whole thing together
                current.setQueryPattern(eg);
                current.setConstructTemplate(new Template(pattern));
                result.add(current);
            }
            return result;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error from mediation process: " + e);
            return null;
        }
    }

}

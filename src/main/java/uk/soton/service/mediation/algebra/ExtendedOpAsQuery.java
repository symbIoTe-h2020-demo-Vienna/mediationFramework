/*
 * ExtendedOpAsQuery.java
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
package uk.soton.service.mediation.algebra;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.SortCondition;
import org.apache.jena.sparql.ARQNotImplemented;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.algebra.OpVisitor;
import org.apache.jena.sparql.algebra.op.OpAssign;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpConditional;
import org.apache.jena.sparql.algebra.op.OpDatasetNames;
import org.apache.jena.sparql.algebra.op.OpDiff;
import org.apache.jena.sparql.algebra.op.OpDisjunction;
import org.apache.jena.sparql.algebra.op.OpDistinct;
import org.apache.jena.sparql.algebra.op.OpExt;
import org.apache.jena.sparql.algebra.op.OpExtend;
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.algebra.op.OpGraph;
import org.apache.jena.sparql.algebra.op.OpGroup;
import org.apache.jena.sparql.algebra.op.OpJoin;
import org.apache.jena.sparql.algebra.op.OpLabel;
import org.apache.jena.sparql.algebra.op.OpLeftJoin;
import org.apache.jena.sparql.algebra.op.OpList;
import org.apache.jena.sparql.algebra.op.OpMinus;
import org.apache.jena.sparql.algebra.op.OpNull;
import org.apache.jena.sparql.algebra.op.OpOrder;
import org.apache.jena.sparql.algebra.op.OpPath;
import org.apache.jena.sparql.algebra.op.OpProcedure;
import org.apache.jena.sparql.algebra.op.OpProject;
import org.apache.jena.sparql.algebra.op.OpPropFunc;
import org.apache.jena.sparql.algebra.op.OpQuad;
import org.apache.jena.sparql.algebra.op.OpQuadBlock;
import org.apache.jena.sparql.algebra.op.OpQuadPattern;
import org.apache.jena.sparql.algebra.op.OpReduced;
import org.apache.jena.sparql.algebra.op.OpSequence;
import org.apache.jena.sparql.algebra.op.OpService;
import org.apache.jena.sparql.algebra.op.OpSlice;
import org.apache.jena.sparql.algebra.op.OpTable;
import org.apache.jena.sparql.algebra.op.OpTopN;
import org.apache.jena.sparql.algebra.op.OpTriple;
import org.apache.jena.sparql.algebra.op.OpUnion;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.core.VarExprList;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementAssign;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementNamedGraph;
import org.apache.jena.sparql.syntax.ElementOptional;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;
import org.apache.jena.sparql.syntax.ElementUnion;

/**
 * Extends the Jena OpAsQuery class in order to render in SPARQL some extension implemented in ARQ.
 *
 * @author Gianluca Correndo <gc3@ecs.soton.ac.uk>
 */
public class ExtendedOpAsQuery extends OpAsQuery {

    /**
     * Translate an algebra operation to the equivalent query
     *
     * @param op ARQ Op to translate
     * @return equivalent SPARQL query
     */
    public static Query asQuery(Op op) {
        Query query = QueryFactory.make();
        query.setQueryResultStar(true);

        Converter v = new Converter(query);
        op.visit(v);

        query.setQueryPattern(v.currentGroup);
        query.setQuerySelectType();

        query.setResultVars();
        return query;
    }

    /**
     * The Converter class implements the ARQ OpVisitor interface.
     *
     * @author Gianluca Correndo <gc3@ecs.soton.ac.uk>
     */
    static public class Converter implements OpVisitor {

        Query query;
        Element element = null;
        ElementGroup currentGroup = null;
        Stack<ElementGroup> stack = new Stack<ElementGroup>();

        public Converter(Query query) {
            this.query = query;
            currentGroup = new ElementGroup();
        }

        Element asElement(Op op) {
            ElementGroup g = asElementGroup(op);
            if (g.getElements().size() == 1) {
                return (Element) g.getElements().get(0);
            }
            return g;
        }

        ElementGroup asElementGroup(Op op) {
            startSubGroup();
            op.visit(this);
            return endSubGroup();
        }

        public void visit(OpBGP opBGP) {
            currentGroup().addElement(process(opBGP.getPattern()));
        }

        public void visit(OpTriple opTriple) {
            currentGroup().addElement(process(opTriple.getTriple()));
        }

        public void visit(OpProcedure opProcedure) {
            throw new ARQNotImplemented("OpProcedure");
        }

        public void visit(OpPropFunc opPropFunc) {
            throw new ARQNotImplemented("OpPropFunc");
        }

        public void visit(OpSequence opSequence) {
            throw new ARQNotImplemented("OpSequence");
        }

        private ElementTriplesBlock process(BasicPattern pattern) {
            ElementTriplesBlock e = new ElementTriplesBlock();
            Iterator<Triple> iter = pattern.iterator();
            for (; iter.hasNext();) {
                Triple t = (Triple) iter.next();
                // Leave bNode variables as they are
                // Query serialization will deal with them.
                e.addTriple(t);
            }
            return e;
        }

        private ElementTriplesBlock process(Triple triple) {
            // Unsubtle
            ElementTriplesBlock e = new ElementTriplesBlock();
            e.addTriple(triple);
            return e;
        }

        public void visit(OpQuadPattern quadPattern) {
            throw new ARQNotImplemented("OpQuadPattern");
        }

        public void visit(OpPath opPath) {
            throw new ARQNotImplemented("OpPath");
        }

        public void visit(OpJoin opJoin) {
            // Keep things clearly separated.
            Element eLeft = asElement(opJoin.getLeft());
            Element eRight = asElementGroup(opJoin.getRight());

            ElementGroup g = currentGroup();
            g.addElement(eLeft);
            g.addElement(eRight);
            return;
        }

        public void visit(OpLeftJoin opLeftJoin) {
            Element eLeft = asElement(opLeftJoin.getLeft());
            Element eRight = asElementGroup(opLeftJoin.getRight());
            ElementGroup g = currentGroup();
            g.addElement(eLeft);
            ElementOptional opt = new ElementOptional(eRight);
            g.addElement(opt);
            /**
             * >>> Add eventual filter sections
             */
            if (opt.getOptionalElement() instanceof ElementGroup) {
                if (opLeftJoin.getExprs() != null) {
                    for (Iterator<Expr> iter = opLeftJoin.getExprs().iterator(); iter.hasNext();) {
                        Expr expr = (Expr) iter.next();
                        ElementFilter f = new ElementFilter(expr);
                        ((ElementGroup) opt.getOptionalElement()).addElement(f);
                    }
                }
            }
        }

        public void visit(OpDiff opDiff) {
            throw new ARQNotImplemented("OpDiff");
        }

        public void visit(OpUnion opUnion) {
            Element eLeft = asElementGroup(opUnion.getLeft());
            Element eRight = asElementGroup(opUnion.getRight());
            if (eLeft instanceof ElementUnion) {
                ElementUnion elUnion = (ElementUnion) eLeft;
                elUnion.addElement(eRight);
                return;
            }

//            if ( eRight instanceof ElementUnion )
//            {
//                ElementUnion elUnion = (ElementUnion)eRight ;
//                elUnion.getElements().add(0, eLeft) ;
//                return ;
//            }
            ElementUnion elUnion = new ElementUnion();
            elUnion.addElement(eLeft);
            elUnion.addElement(eRight);
            currentGroup().addElement(elUnion);
        }

        public void visit(OpConditional opCondition) {
            throw new ARQNotImplemented("OpCondition");
        }

        public void visit(OpFilter opFilter) {
            // (filter .. (filter ( ... ))   (non-canonicalizing OpFilters)
            // Inner gets Grouped unnecessarily.
            Element e = asElement(opFilter.getSubOp());
            if (currentGroup() != e) {
                currentGroup().addElement(e);
            }
            element = currentGroup();      // Was cleared by asElement.

            ExprList exprs = opFilter.getExprs();
            for (Iterator<Expr> iter = exprs.iterator(); iter.hasNext();) {
                Expr expr = (Expr) iter.next();
                ElementFilter f = new ElementFilter(expr);
                currentGroup().addElement(f);
            }
        }

        public void visit(OpGraph opGraph) {
            startSubGroup();
            Element e = asElement(opGraph.getSubOp());
            //ElementGroup g = endSubGroup();

            Element graphElt = new ElementNamedGraph(opGraph.getNode(), e);
            currentGroup().addElement(graphElt);
        }

        public void visit(OpService opService) {
            throw new ARQNotImplemented("OpService");
        }

        public void visit(OpDatasetNames dsNames) {
            throw new ARQNotImplemented("OpDatasetNames");
        }

        public void visit(OpTable opTable) {
            throw new ARQNotImplemented("OpTable");
        }

        public void visit(OpExt opExt) {
            throw new ARQNotImplemented("OpExt");
        }

        public void visit(OpNull opNull) {
            throw new ARQNotImplemented("OpNull");
        }

        public void visit(OpLabel opLabel) {
            throw new ARQNotImplemented("OpLabel");
        }

        public void visit(OpAssign opAssign) {
            ElementGroup subEl = asElementGroup(opAssign.getSubOp());
            this.currentGroup().addElement(subEl);
            VarExprList exprList = opAssign.getVarExprList();
            for (Var var : (List<Var>) exprList.getVars()) {
                Element assign = new ElementAssign(var, exprList.getExpr(var));
                subEl.addElement(assign);
            }
        }

        public void visit(OpList opList) {
            System.out.println("done nothing for:" + opList);
        }

        public void visit(OpOrder opOrder) {
            List<SortCondition> x = opOrder.getConditions();
            Iterator<SortCondition> iter = x.iterator();
            for (; iter.hasNext();) {
                SortCondition sc = (SortCondition) iter.next();
                query.addOrderBy(sc);
            }
            opOrder.getSubOp().visit(this);
        }

        public void visit(OpProject opProject) {
            query.setQueryResultStar(false);
            Iterator<Var> iter = opProject.getVars().iterator();
            for (; iter.hasNext();) {
                Var v = (Var) iter.next();
                query.addResultVar(v);
            }
            opProject.getSubOp().visit(this);
        }

        public void visit(OpReduced opReduced) {
            query.setReduced(true);
            opReduced.getSubOp().visit(this);
        }

        public void visit(OpDistinct opDistinct) {
            query.setDistinct(true);
            opDistinct.getSubOp().visit(this);
        }

        public void visit(OpSlice opSlice) {
            if (opSlice.getStart() != Query.NOLIMIT) {
                query.setOffset(opSlice.getStart());
            }
            if (opSlice.getLength() != Query.NOLIMIT) {
                query.setLimit(opSlice.getLength());
            }
            opSlice.getSubOp().visit(this);
        }

        @SuppressWarnings("unused")
        private Element lastElement() {
            ElementGroup g = currentGroup;
            if (g == null || g.getElements().size() == 0) {
                return null;
            }
            int len = g.getElements().size();
            return (Element) g.getElements().get(len - 1);
        }

        private void startSubGroup() {
            push(currentGroup);
            ElementGroup g = new ElementGroup();
            currentGroup = g;
        }

        private ElementGroup endSubGroup() {
            ElementGroup g = pop();
            ElementGroup r = currentGroup;
            currentGroup = g;
            return r;
        }

        private ElementGroup currentGroup() {
            return currentGroup;
        }

        @SuppressWarnings("unused")
        private ElementGroup peek() {
            if (stack.size() == 0) {
                return null;
            }
            return (ElementGroup) stack.peek();
        }

        private ElementGroup pop() {
            return (ElementGroup) stack.pop();
        }

        private void push(ElementGroup el) {
            stack.push(el);
        }

        @Override
        public void visit(OpQuadBlock arg0) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void visit(OpQuad arg0) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void visit(OpExtend arg0) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void visit(OpMinus arg0) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void visit(OpDisjunction arg0) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void visit(OpGroup arg0) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void visit(OpTopN arg0) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
}

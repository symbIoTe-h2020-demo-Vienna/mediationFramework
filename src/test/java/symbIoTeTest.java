
import fr.inrialpes.exmo.align.impl.renderer.RDFRendererVisitor;
import fr.inrialpes.exmo.align.parser.AlignmentParser;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.Transform;
import org.apache.jena.sparql.algebra.Transformer;
import org.apache.jena.tdb.TDBFactory;
import org.junit.Test;
import org.semanticweb.owl.align.AlignmentException;
import org.semanticweb.owl.align.AlignmentVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.soton.service.mediation.Alignment;
import uk.soton.service.mediation.EntityTranslationService;
import uk.soton.service.mediation.EntityTranslationServiceImpl;
import uk.soton.service.mediation.algebra.EntityTranslation;
import uk.soton.service.mediation.algebra.ExtendedOpAsQuery;
import uk.soton.service.mediation.edoal.EDOALMediator;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author jab
 */
public class symbIoTeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(symbIoTeTest.class);

    @Test
    public void test1() throws UnsupportedEncodingException, AlignmentException, IOException {
        Alignment a = null;
        AlignmentParser parser = new AlignmentParser(0);
        parser.initAlignment(null);
        try {
            a = EDOALMediator.mediate(parser.parse("file:./resources/example.xml"));
        } catch (AlignmentException e) {
            LOGGER.error("Couldn't load the alignment:", e);
        }
        String queryString = "PREFIX core: <https://www.symbiote-h2020.eu/ontology/core#> \n"
                + "PREFIX plA: <http://www.example.com/ontology/platformA/> \n"
                + "PREFIX plB: <http://www.example.com/ontology/platformB/> \n"
                + "\n"
                + "SELECT ?description ?color WHERE {\n"
                + "	?sensor a core:Sensor ;\n"
                + "			core:description ?description ;\n"
                + "			plA:hasColor ?color .\n"
                + "} ";
        Query query = QueryFactory.create(queryString, Syntax.syntaxARQ);
        Op op = Algebra.compile(query);
        EntityTranslationService ets = new EntityTranslationServiceImpl();
        Transform translation = new EntityTranslation(ets, a);
        Op translated = Transformer.transform(translation, op);
        Query queryt = ExtendedOpAsQuery.asQuery(translated);
        System.out.println("Original query:\n" + queryString);
        System.out.println("Modified query:\n" + queryt);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(out, "UTF-8")), true);
        AlignmentVisitor renderer = new RDFRendererVisitor(writer);
        parser.parse("file:./resources/example.xml").render(renderer);
        writer.flush();
        writer.close();

        ByteArrayOutputStream out2 = new ByteArrayOutputStream();

        Model model = ModelFactory.createDefaultModel();
        model.read(new ByteArrayInputStream(out.toByteArray()), null, "RDFXML");
        Dataset dataset = TDBFactory.createDataset();//DIRECTORY);
        dataset.getDefaultModel().add(model);
        System.out.println("------------------------");
        model.write(System.out, "RDFXML");
        model.write(out2, "RDFXML");

        String mapping2 = out2.toString();

        // now read back in to EDOAL
        //InputStream in2 = new ByteArrayInputStream(out2.toByteArray());
        a = null;

        parser.initAlignment(null);
        try {
            a = EDOALMediator.mediate(parser.parseString(mapping2));
        } catch (AlignmentException e) {
            LOGGER.error("Couldn't load the alignment:", e);
        }

        query = QueryFactory.create(queryString, Syntax.syntaxARQ);
        op = Algebra.compile(query);
        ets = new EntityTranslationServiceImpl();
        translation = new EntityTranslation(ets, a);
        translated = Transformer.transform(translation, op);
        queryt = ExtendedOpAsQuery.asQuery(translated);
        System.out.println("Original query:\n" + queryString);
        System.out.println("Modified query:\n" + queryt);
        String t = "";
    }
}

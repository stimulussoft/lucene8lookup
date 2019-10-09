package com.stimulus.lucene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Random;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.lucene.document.*;
import org.junit.jupiter.api.*;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;


public class LookupTest {

    public static final int ROUNDS = 1000000;
    private static final String CHARLIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
    private Path indexDirectoryPath;
    private static Random r = new Random();
    protected static final HashFunction hf = Hashing.murmur3_128();

    public LookupTest()  {
    }

    public String genStr(Random random, int length) {
        char[] text = new char[length];
        for (int i = 0; i < length; i++) {
            text[i] = CHARLIST.charAt(random.nextInt(CHARLIST.length()));
        }
        return new String(text);
    }
    @Test
    public void testLookup() throws Exception {
        try {
            init();
            System.out.println("***********");
            index(false);
            search(false);
            System.out.println("***********");
            index(true);
            search(true);
        } finally {
            cleanup();
        }
    }

    private void init() throws IOException {
        this.indexDirectoryPath = Files.createTempDirectory("lookup");
        System.out.println(ROUNDS + " docs");
    }

    private void cleanup() throws IOException {
        if (Objects.nonNull(indexDirectoryPath))
            MoreFiles.deleteRecursively(indexDirectoryPath, RecursiveDeleteOption.ALLOW_INSECURE);
    }

    private void index(boolean numeric) throws IOException {

        IndexWriterConfig config = new IndexWriterConfig();
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        try ( Directory indexDirectory = FSDirectory.open(indexDirectoryPath);
              IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            System.out.println("Indexing start " + sig (numeric));
            for (int i = 0; i < ROUNDS; i++) {
                Document doc = new Document();
                if (numeric) {
                    doc.add(new LongPoint("uid", idNum(i)));
                    doc.add(new SortedNumericDocValuesField("uid",idNum(i)));
                } else {
                    doc.add(new SortedDocValuesField("uid", new BytesRef(id(i))));
                    doc.add(new StringField("uid", new BytesRef(id(i)), Field.Store.YES));
                }
                doc.add(new StringField("fuzz", new BytesRef(genStr(r, 256)), Field.Store.YES));
                writer.addDocument(doc);
            }
            System.out.println("Indexing complete " + sig (numeric));
        }
    }

    private String id(int i) {
        HashCode hashCode = hf.newHasher().putInt(i).hash();
        return Long.toHexString(hashCode.padToLong());
    }


    private long idNum(int i) {
        HashCode hashCode = hf.newHasher().putInt(i).hash();
        return hashCode.padToLong();
    }

    private String[] ids() {
        String[] ids = new String[ROUNDS];
        for (int i = 0 ; i < ROUNDS; i ++) {
            ids[i] = id(i);
        }
        return ids;
    }


    private long[] idNums() {
       long[] ids = new long[ROUNDS];
        for (int i = 0 ; i < ROUNDS; i ++) {
            ids[i] = idNum(i);
        }
        return ids;
    }

    private String sig(boolean numeric) {
        return (numeric ? "numeric" : "string");
    }

    private void search(boolean numeric) throws IOException {
        try (Directory searchDirectory = FSDirectory.open(indexDirectoryPath);
             IndexReader reader = DirectoryReader.open(searchDirectory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocsCollector<?> tfc = TopScoreDocCollector.create(ROUNDS, ROUNDS);
            long start;
            if (numeric) {
                long[] ids = idNums();
                start =  System.nanoTime();
                searcher.search(new  DocValuesNumbersQuery("uid", ids), tfc);
                Assertions.assertEquals(ids.length, tfc.getTotalHits());
            } else {
                String[] ids = ids();
                start =  System.nanoTime();
                searcher.search(new DocValuesTermsQuery("uid", ids), tfc);
                Assertions.assertEquals(ids.length, tfc.getTotalHits());
            }
            long end = System.nanoTime();
            double elapsedTimeInSecond = (double) (end-start) / 1_000_000_000;
            System.out.println("search "+ sig (numeric) + " complete in "+elapsedTimeInSecond+" secs");
        }

    }

}
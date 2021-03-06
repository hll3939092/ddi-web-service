package uk.ac.ebi.ddi.ws.modules.dataset.util;

import uk.ac.ebi.ddi.ebe.ws.dao.model.common.Entry;
import uk.ac.ebi.ddi.ebe.ws.dao.model.common.QueryResult;
import uk.ac.ebi.ddi.ebe.ws.dao.model.dataset.TermResult;
import uk.ac.ebi.ddi.service.db.service.dataset.IDatasetService;
import uk.ac.ebi.ddi.service.db.service.dataset.IMostAccessedDatasetService;
import uk.ac.ebi.ddi.service.db.service.similarity.CitationService;
import uk.ac.ebi.ddi.service.db.service.similarity.EBIPubmedSearchService;
import uk.ac.ebi.ddi.service.db.service.similarity.ReanalysisDataService;
import uk.ac.ebi.ddi.ws.modules.dataset.model.DataSetResult;
import uk.ac.ebi.ddi.ws.modules.dataset.model.DatasetDetail;
import uk.ac.ebi.ddi.ws.modules.dataset.model.DatasetSummary;
import uk.ac.ebi.ddi.ws.modules.dataset.model.Organism;
import uk.ac.ebi.ddi.ws.modules.term.model.Term;
import uk.ac.ebi.ddi.ws.util.Constants;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Yasset Perez-Riverol ypriverol
 */
public class RepoDatasetMapper {

    public static CitationService citationService;

    public static EBIPubmedSearchService ebiPubmedSearchService;

    public static ReanalysisDataService reanalysisDataService;

    public static IMostAccessedDatasetService mostAccessedDatasetService;

    public static IDatasetService datasetService;

    private RepoDatasetMapper() {
    }

    /**
     * Transform the information form the query to the Web service strucutre
     * @param queryResults The original results from the Query
     * @return             The set of datasets form the query
     */
    public static DataSetResult asDataSummary(QueryResult queryResults, QueryResult taxonomies) {

        Map<String, String> taxonomyMap = RepoDatasetMapper.getTaxonomyMap(taxonomies);

        DataSetResult dataset = new DataSetResult();

        List<DatasetSummary> datasets = new ArrayList<>();

        dataset.setCount(queryResults.getCount());

        if (queryResults.getFacets() != null && queryResults.getFacets().length > 0) {
            dataset.setFacets(Arrays.asList(queryResults.getFacets()));
        }

        if (queryResults.getEntries() != null && queryResults.getEntries().length > 0) {
            for (Entry entry: queryResults.getEntries()) {
                datasets.add(RepoDatasetMapper.transformDatasetSummary(entry, taxonomyMap));
            }
        }
        dataset.setDatasets(datasets);

        return dataset;
    }

    /**
     * Retrieve the information of each taxonomy as id + name
     * @param taxonomies A list of taxonomies
     * @return The map of the taxonomies as Map<id, name>
     */
    private static Map<String, String> getTaxonomyMap(QueryResult taxonomies) {
        Map<String, String> taxonomyMap  = new HashMap<>();

        if (taxonomies != null && taxonomies.getEntries() != null && taxonomies.getEntries().length > 0) {
            for (Entry entry: taxonomies.getEntries()) {
                if (entry != null && entry.getFields() != null && entry.getFields().containsKey(Constants.TAXONOMY_NAME)
                        && entry.getFields().get(Constants.TAXONOMY_NAME).length > 0
                        && entry.getFields().get(Constants.TAXONOMY_NAME)[0] != null) {
                    taxonomyMap.put(entry.getId(), entry.getFields().get(Constants.TAXONOMY_NAME)[0]);
                }
            }
        }
        return taxonomyMap;
    }

    /**
     * Transform a web-service entry to a DatasetSummary
     * @param entry the original entry from the dataset
     * @param taxonomyMap The map of all taxonomies included in this query
     * @return a Dataset Summary
     */
    private static DatasetSummary transformDatasetSummary(Entry entry, Map<String, String> taxonomyMap) {

        DatasetSummary datasetSummary = new DatasetSummary();

        if (entry == null) {
            return datasetSummary;
        }
        datasetSummary.setId(entry.getId());
        datasetSummary.setSource(entry.getSource());

        if (entry.getFields() == null) {
            return datasetSummary;
        }

        Map<String, String[]> fields = entry.getFields();

        if (fields.get(Constants.NAME_FIELD) != null  && fields.get(Constants.NAME_FIELD).length > 0) {
            datasetSummary.setTitle(fields.get(Constants.NAME_FIELD)[0]);
        }

        if (fields.get(Constants.DESCRIPTION_FIELD) != null && fields.get(Constants.DESCRIPTION_FIELD).length > 0) {
            datasetSummary.setDescription(fields.get(Constants.DESCRIPTION_FIELD)[0]);
        }

        if (fields.get(Constants.PUB_DATE_FIELD) != null && fields.get(Constants.PUB_DATE_FIELD).length > 0) {
            datasetSummary.setPublicationDate(fields.get(Constants.PUB_DATE_FIELD)[0]);
        }

        List<String> keywords = new ArrayList<>();

        if (fields.get(Constants.CURATOR_KEY_FIELD) != null && fields.get(Constants.CURATOR_KEY_FIELD).length > 0) {
            keywords.addAll(formatKeywords(Arrays.asList(fields.get(Constants.CURATOR_KEY_FIELD))));
        }

        if (fields.get(Constants.OMICS_TYPE_FIELD) != null && fields.get(Constants.OMICS_TYPE_FIELD).length > 0) {
            datasetSummary.setOmicsType(Arrays.asList(fields.get(Constants.OMICS_TYPE_FIELD)));
        }

        if (fields.get(Constants.SUBMITTER_KEY_FIELD) != null && fields.get(Constants.SUBMITTER_KEY_FIELD).length > 0) {
            keywords.addAll(formatKeywords(Arrays.asList(fields.get(Constants.SUBMITTER_KEY_FIELD))));
        }

        if (fields.get(Constants.CITATION_COUNT) != null && fields.get(Constants.CITATION_COUNT).length > 0) {
            datasetSummary.setCitationsCount(Integer.valueOf(fields.get(Constants.CITATION_COUNT)[0]));
        }

        if (fields.get(Constants.SEARCH_COUNT) != null && fields.get(Constants.SEARCH_COUNT).length > 0) {
            datasetSummary.setConnectionsCount(Integer.valueOf(fields.get(Constants.SEARCH_COUNT)[0]));
        }

        if (fields.get(Constants.VIEW_COUNT) != null && fields.get(Constants.VIEW_COUNT).length > 0) {
            datasetSummary.setViewsCount(Integer.valueOf(fields.get(Constants.VIEW_COUNT)[0]));
        }

        if (fields.get(Constants.REANALYZED_COUNT) != null && fields.get(Constants.REANALYZED_COUNT).length > 0) {
            datasetSummary.setReanalysisCount(Integer.valueOf(fields.get(Constants.REANALYZED_COUNT)[0]));
        }

        if (fields.get(Constants.DOWNLOAD_COUNT) != null && fields.get(Constants.DOWNLOAD_COUNT).length > 0) {
            datasetSummary.setDownloadCount(Integer.valueOf(fields.get(Constants.DOWNLOAD_COUNT)[0]
                    .replace(".0", "")));
        }

        if (fields.get(Constants.DOWNLOAD_COUNT_SCALED) != null
                && fields.get(Constants.DOWNLOAD_COUNT_SCALED).length > 0) {
            datasetSummary.setDownloadCountScaled(Double.valueOf(fields.get(Constants.DOWNLOAD_COUNT_SCALED)[0]));
        }

        if (fields.get(Constants.CITATION_COUNT_SCALED) != null
                && fields.get(Constants.CITATION_COUNT_SCALED).length > 0) {
            datasetSummary.setCitationsCountScaled(Double.valueOf(fields.get(Constants.CITATION_COUNT_SCALED)[0]));
        }

        if (fields.get(Constants.SEARCH_COUNT_SCALED) != null && fields.get(Constants.SEARCH_COUNT_SCALED).length > 0) {
            datasetSummary.setConnectionsCountScaled(Double.valueOf(fields.get(Constants.SEARCH_COUNT_SCALED)[0]));
        }

        if (fields.get(Constants.VIEW_COUNT_SCALED) != null && fields.get(Constants.VIEW_COUNT_SCALED).length > 0) {
            datasetSummary.setViewsCountScaled(Double.valueOf(fields.get(Constants.VIEW_COUNT_SCALED)[0]));
        }

        if (fields.get(Constants.REANALYZED_COUNT_SCALED) != null
                && fields.get(Constants.REANALYZED_COUNT_SCALED).length > 0) {
            datasetSummary.setReanalysisCountScaled(Double.valueOf(fields.get(Constants.REANALYZED_COUNT_SCALED)[0]));
        }

        if (keywords.size() > 0) {
            String[] arrayKeywords = new String[keywords.size()];
            for (int i = 0; i < keywords.size(); i++) {
                arrayKeywords[i] = keywords.get(i);
            }
            datasetSummary.setKeywords(arrayKeywords);
        }

        List<Organism> organisms = new ArrayList<>();

        if (fields.containsKey(Constants.TAXONOMY_FIELD)) {
            if (fields.get(Constants.TAXONOMY_FIELD) != null && fields.get(Constants.TAXONOMY_FIELD).length > 0) {
                for (String taxonomyId: fields.get(Constants.TAXONOMY_FIELD)) {
                    organisms.add(new Organism(taxonomyId, taxonomyMap.get(taxonomyId)));
                }
            }
        }
        datasetSummary.setOrganisms(organisms);

        return datasetSummary;
    }

    private static Collection<? extends String> formatKeywords(List<String> strings) {
        List<String> newKeywords;
        if (strings != null && !strings.isEmpty()) {
            newKeywords = new ArrayList<>();
            for (String oldkeyword: strings) {
                Pattern pattern = Pattern.compile(";");
                Pattern pattern2 = Pattern.compile("；");
                String[] split = pattern.split(oldkeyword, 0);
                String[] split2 = pattern2.split(oldkeyword, 0);
                if (split.length > 1) {
                    newKeywords.addAll(Arrays.asList(split));
                } else if (split2.length > 1) {
                    newKeywords.addAll(Arrays.asList(split2));
                } else {
                    newKeywords.add(oldkeyword);
                }
            }
            strings = newKeywords;
        }
        return strings;
    }

    /**
     * Returns the Terms frequency List
     * @param termResult terms from the web service
     * @return List of terms
     */
    public static List<Term> asTermResults(TermResult termResult) {
        List<Term> terms = new ArrayList<>();
        if (termResult != null && termResult.getTerms() != null && termResult.getTerms().length > 0) {
            for (uk.ac.ebi.ddi.ebe.ws.dao.model.common.Term oldTerm: termResult.getTerms()) {
                terms.add(new Term(oldTerm.getText(), oldTerm.getFrequency()));
            }
        }
        return terms;
    }

    /**
     * Return a set of taxonomy ids from the Dataset List
     * @param queryResult the datasets to be anaylzed
     * @return a list of taxonomy ids
     */
    public static Set<String> getTaxonomyIds(QueryResult queryResult) {
        Set<String> ids = new HashSet<>();
        if (queryResult != null && queryResult.getEntries() != null && queryResult.getEntries().length > 0) {
            for (Entry entry: queryResult.getEntries()) {
               if (entry.getFields() != null && entry.getFields().containsKey(Constants.TAXONOMY_FIELD)) {
                   Collections.addAll(ids, entry.getFields().get(Constants.TAXONOMY_FIELD));
               }
            }
        }
        return ids;
    }

    /**
     * Merge a set of queries and retrieve only one query. This function is specially interesting
     * when you have more than the limits of the entries to be query (100).
     * We are not merging the facets now because it is not interesting but in the future we can do it.
     * @param resultList List of QueryResult to be merge
     * @return One big QueryResult
     */
    public static QueryResult mergeQueryResult(List<QueryResult> resultList) {
        QueryResult result = new QueryResult();
        List<Entry> entries = new ArrayList<>();

        for (QueryResult query: resultList) {
            entries.addAll(Arrays.asList(query.getEntries()));
        }

        Entry[] entryArray = new Entry[entries.size()];

        for (int i = 0; i < entries.size(); i++) {
            entryArray[i] = entries.get(i);
        }

        result.setEntries(entryArray);
        result.setCount(entryArray.length);
        return result;
    }

    public static DatasetDetail addTaxonomy(DatasetDetail datasetDetail, QueryResult taxonomies) {
        List<Organism> organismList = new ArrayList<>();
        if (taxonomies != null && taxonomies.getEntries() != null && taxonomies.getEntries().length > 0) {
            for (Entry entry: taxonomies.getEntries()) {
                if (entry != null) {
                    String acc = entry.getId();
                    String name = entry.getFields().get(Constants.TAXONOMY_NAME)[0];
                    Organism organism = new Organism(acc, name);
                    organismList.add(organism);
                }
            }
        }
        datasetDetail.setOrganisms(organismList);
        return datasetDetail;
    }
}

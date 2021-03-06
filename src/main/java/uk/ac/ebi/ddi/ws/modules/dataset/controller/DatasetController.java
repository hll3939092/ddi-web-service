package uk.ac.ebi.ddi.ws.modules.dataset.controller;

/**
 * @author Yasset Perez-Riverol ypriverol
 */

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.ddi.ebe.ws.dao.client.dataset.DatasetWsClient;
import uk.ac.ebi.ddi.ebe.ws.dao.client.dictionary.DictionaryClient;
import uk.ac.ebi.ddi.ebe.ws.dao.client.domain.DomainWsClient;
import uk.ac.ebi.ddi.ebe.ws.dao.model.common.Entry;
import uk.ac.ebi.ddi.ebe.ws.dao.model.common.QueryResult;
import uk.ac.ebi.ddi.ebe.ws.dao.model.dataset.SimilarResult;
import uk.ac.ebi.ddi.service.db.model.dataset.*;
import uk.ac.ebi.ddi.service.db.model.logger.DatasetResource;
import uk.ac.ebi.ddi.service.db.model.logger.HttpEvent;
import uk.ac.ebi.ddi.service.db.repo.facetsettings.FacetSettingsRepository;
import uk.ac.ebi.ddi.service.db.service.database.DatabaseDetailService;
import uk.ac.ebi.ddi.service.db.service.dataset.*;
import uk.ac.ebi.ddi.service.db.service.enrichment.EnrichmentInfoService;
import uk.ac.ebi.ddi.service.db.service.logger.DatasetResourceService;
import uk.ac.ebi.ddi.service.db.service.logger.HttpEventService;
import uk.ac.ebi.ddi.service.db.service.similarity.CitationService;
import uk.ac.ebi.ddi.service.db.service.similarity.EBIPubmedSearchService;
import uk.ac.ebi.ddi.service.db.service.similarity.ReanalysisDataService;
import uk.ac.ebi.ddi.ws.modules.dataset.model.*;
import uk.ac.ebi.ddi.ws.modules.dataset.util.FacetViewAdapter;
import uk.ac.ebi.ddi.ws.modules.dataset.util.RepoDatasetMapper;
import uk.ac.ebi.ddi.ws.modules.security.UserPermissionService;
import uk.ac.ebi.ddi.ws.util.Constants;
import uk.ac.ebi.ddi.ws.util.WsUtilities;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static uk.ac.ebi.ddi.ws.util.WsUtilities.tranformServletResquestToEvent;
import static uk.ac.ebi.ddi.ws.util.WsUtilities.transformSimilarDatasetSummary;


@Api(value = "dataset", description = "Retrieve the information about the dataset including search functionalities")
@Controller
@RequestMapping(value = "/dataset")
public class DatasetController {

    @Autowired
    DatasetWsClient dataWsClient;

    @Autowired
    DomainWsClient domainWsClient;

    @Autowired
    private DatasetResourceService resourceService;

    @Autowired
    HttpEventService eventService;

    @Autowired
    private DictionaryClient dictionaryClient;

    @Autowired
    IDatasetSimilarsService datasetSimilarsService;

    IDatasetService datasetService;

    @Autowired
    IUnMergeDatasetService iUnMergeDatasetService;

    @Autowired
    FacetSettingsRepository facetSettingsRepository;

    @Autowired
    DatabaseDetailService databaseDetailService;

    //autowired by ctor param
    IMostAccessedDatasetService mostAccessedDatasetService;
    CitationService citationService;
    EBIPubmedSearchService ebiPubmedSearchService;
    ReanalysisDataService reanalysisDataService;
    EnrichmentInfoService enrichmentService;
    UnMergeDatasetService unMergeDatasetService;

    @Autowired
    UserPermissionService userPermissionService;

    @Autowired
    public DatasetController(CitationService citationService,
                             EBIPubmedSearchService ebiPubmedSearchService,
                             ReanalysisDataService reanalysisDataService,
                             IMostAccessedDatasetService mostAccessedDatasetService,
                             IDatasetService datasetService,
                             EnrichmentInfoService enrichmentService,
                             UnMergeDatasetService unMergeDatasetService,
                             UserPermissionService userPermissionService) {
        RepoDatasetMapper.ebiPubmedSearchService = ebiPubmedSearchService;
        RepoDatasetMapper.mostAccessedDatasetService = mostAccessedDatasetService;
        RepoDatasetMapper.citationService = citationService;
        RepoDatasetMapper.reanalysisDataService = reanalysisDataService;
        RepoDatasetMapper.datasetService = datasetService;


        this.userPermissionService = userPermissionService;
        this.mostAccessedDatasetService = mostAccessedDatasetService;
        this.citationService = citationService;
        this.ebiPubmedSearchService = ebiPubmedSearchService;
        this.reanalysisDataService = reanalysisDataService;
        this.datasetService = datasetService;
        this.enrichmentService = enrichmentService;
        this.unMergeDatasetService = unMergeDatasetService;
    }

    //@CrossOrigin
    @ApiOperation(value = "Search for datasets in the resource", position = 1,
            notes = "Retrieve datasets in the resource using different queries")
    @RequestMapping(value = "/search", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public DataSetResult search(
            @ApiParam(value = "General search term against multiple fields including, e.g: cancer human")
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @ApiParam(value = "Field to sort the output of the search results, e.g:  id, publication_date")
            @RequestParam(value = "sortfield", required = false, defaultValue = "") String sortfield,
            @ApiParam(value = "Type of sorting ascending or descending, e.g: ascending")
            @RequestParam(value = "order", required = false, defaultValue = "") String order,
            @ApiParam(value = "The starting point for the search, e.g: 0")
            @RequestParam(value = "start", required = false, defaultValue = "0") int start,
            @ApiParam(value = "The number of records to be retrieved, e.g: maximum 100")
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            @ApiParam(value = "The starting point for the search, e.g: 0")
            @RequestParam(value = "faceCount", required = false, defaultValue = "20") int facetCount) {

        query = (query == null || query.isEmpty()) ? "*:*" : query;

        query = query + " NOT (isprivate:true)";
        query = modifyIfSearchByYear(query);

        QueryResult queryResult = dataWsClient.getDatasets(
                Constants.MAIN_DOMAIN, query, Constants.DATASET_SUMMARY, sortfield, order, start, size, facetCount);

        QueryResult taxonomies = null;

        Set<String> taxonomyIds = RepoDatasetMapper.getTaxonomyIds(queryResult);

        // The number of queries should be controlled using the maximum QUERY threshold in this case 100 entries
        // for the EBE web service.
        if (taxonomyIds.size() > Constants.HIGH_QUERY_THRESHOLD) {
            List<QueryResult> results = new ArrayList<>();
            List<String> list = new ArrayList<>(taxonomyIds);
            int count = 0;
            for (int i = 0; i < taxonomyIds.size(); i += Constants.HIGH_QUERY_THRESHOLD) {
                Set<String> currentIds;
                if ((i + Constants.HIGH_QUERY_THRESHOLD) < taxonomyIds.size()) {
                    currentIds = new HashSet<>(list.subList(i, i + Constants.HIGH_QUERY_THRESHOLD));
                } else {
                    currentIds = new HashSet<>(list.subList(i, taxonomyIds.size() - 1));
                }
                results.add(
                        dataWsClient.getDatasetsById(Constants.TAXONOMY_DOMAIN, Constants.TAXONOMY_FIELDS, currentIds));
                count = i;
            }
            Set<String> currentIds = new HashSet<>(list.subList(count, taxonomyIds.size() - 1));
            results.add(dataWsClient.getDatasetsById(Constants.TAXONOMY_DOMAIN, Constants.TAXONOMY_FIELDS, currentIds));
            taxonomies = RepoDatasetMapper.mergeQueryResult(results);

        } else if (taxonomyIds.size() > 0) {
           taxonomies = dataWsClient.getDatasetsById(Constants.TAXONOMY_DOMAIN, Constants.TAXONOMY_FIELDS, taxonomyIds);
        }

        if (queryResult.getCount() > 0) {
            queryResult.setFacets((new FacetViewAdapter(facetSettingsRepository)).process(queryResult.getFacets()));
        }

        return RepoDatasetMapper.asDataSummary(queryResult, taxonomies);
    }

    private String modifyIfSearchByYear(String query) {
        Pattern pattern = Pattern.compile("publication_date:\"\\s*(\\d{4})\"");
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            String matchedYear = matcher.group().replaceAll("publication_date:\"\\s*(\\d{4})\"", "$1");
            String searchByYear = "[" + matchedYear + "0000 TO " + matchedYear + "1231]";
            return query.replaceAll("publication_date:\"\\s*(\\d{4})\"", "publication_date:" + searchByYear);
        }
        return query;
    }

    @ApiOperation(value = "Retrieve the latest datasets in the repository", position = 1,
            notes = "Retrieve the latest datasets in the repository")
    @RequestMapping(value = "/latest", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public DataSetResult latest(
            @ApiParam(value = "Number of terms to be retrieved, e.g : maximum 100, default 20")
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {

        String query = "*:*";
        return search(query, Constants.PUB_DATE_FIELD, "descending", 0, size, 10);
    }

    @ApiOperation(value = "Retrieve an Specific Dataset", position = 1, notes = "Retrieve an specific dataset")
    @RequestMapping(value = "/{domain}/{acc}", method = RequestMethod.GET,
            produces = {APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public OmicsDataset getDataset(
            @ApiParam(value = "Accession of the Dataset in the resource, e.g : PXD000210")
            @PathVariable(value = "acc") String acc,
            @ApiParam(value = "Database accession id, e.g: pride")
            @PathVariable(value = "domain") String domain) {
        String database = databaseDetailService.retriveAnchorName(domain);

        Dataset dataset = datasetService.read(acc, database);
        return new OmicsDataset(dataset);

    }

    @ApiOperation(value = "Retrieve a batch of datasets", position = 1, notes = "Retrieve an specific dataset")
    @RequestMapping(value = "/batch", method = RequestMethod.GET, produces = {APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public Map<String, Object> getMultipleDatasets(
            @ApiParam(value = "List of accessions, matching database by index")
            @RequestParam(value = "acc") String[] accessions,
            @ApiParam(value = "List of databases, matching accession by index")
            @RequestParam(value = "database") String[] databases) {
        if (accessions.length != databases.length) {
            throw new IllegalArgumentException("The amounts of accessions and databases are not match");
        }

        Map<String, Object> result = new HashMap<>();

        List<DatasetDetail> datasets = new ArrayList<>();
        List<String> failure = new ArrayList<>();
        for (int i = 0; i < accessions.length; i++) {
            String acc = accessions[i];
            String domain = databases[i];
            try {
                DatasetDetail datasetDetail = new DatasetDetail();
                Dataset dsResult = datasetService.read(acc, databaseDetailService.retriveAnchorName(domain));

                datasetDetail = getBasicDatasetInfo(datasetDetail, dsResult);
                datasets.add(datasetDetail);
            } catch (Exception e) {
                failure.add(acc);
            }
        }
        result.put("datasets", datasets);
        result.put("failure", failure);
        return result;
    }

    @ApiOperation(value = "Retrieve an Specific Dataset", position = 1, notes = "Retrieve an specific dataset")
    @RequestMapping(value = "/get", method = RequestMethod.GET, produces = {APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public DatasetDetail get(
            @ApiParam(value = "Accession of the Dataset in the resource, e.g : PXD000210")
            @RequestParam(value = "acc", required = true) String acc,
            @ApiParam(value = "Database accession id, e.g: pride")
            @RequestParam(value = "database", required = true) String domain,
            HttpServletRequest httpServletRequest, HttpServletResponse resp) {
        acc = acc.replaceAll("\\s", "");

        DatasetDetail datasetDetail = new DatasetDetail();
        Dataset dsResult = datasetService.read(acc, databaseDetailService.retriveAnchorName(domain));

        datasetDetail = getBasicDatasetInfo(datasetDetail, dsResult);
        datasetDetail = getDatasetInfo(datasetDetail, dsResult);

        // Trace the access to the dataset
        DatasetResource resource = resourceService.read(acc, domain);
        if (resource == null) {
                resource = new DatasetResource("http://www.omicsdi.org/" + domain + "/" + acc, acc, domain);
            resource = resourceService.save(resource);
        }

        HttpEvent event = tranformServletResquestToEvent(httpServletRequest);
        event.setResource(resource);
        eventService.save(event);
        DatasetSimilars similars = datasetSimilarsService.read(acc, databaseDetailService.retriveAnchorName(domain));
        datasetDetail = WsUtilities.mapSimilarsToDatasetDetails(datasetDetail, similars);

        return datasetDetail;
    }


    @ApiOperation(value = "Retrieve an Specific Dataset", position = 1, notes = "Retrieve an specific dataset")
    @RequestMapping(value = "/mostAccessed", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public DataSetResult getMostAccessed(
            @ApiParam(value = "The most accessed datasets size, e.g: 20")
            @RequestParam(value = "size", required = true, defaultValue = "20") int size) {

            DataSetResult result = new DataSetResult();
            List<DatasetSummary> datasetSummaryList = new ArrayList<>();
            Page<MostAccessedDatasets> datasets = mostAccessedDatasetService.readAll(0, size);
            for (MostAccessedDatasets dataset : datasets.getContent()) {
                    DatasetSummary datasetSummary = new DatasetSummary();
                    datasetSummary.setTitle(dataset.getName());
                    datasetSummary.setViewsCount(dataset.getTotal());
                    datasetSummary.setSource(databaseDetailService.retriveSolrName(dataset.getDatabase()));
                    datasetSummary.setId(dataset.getAccession());
                    if (dataset.getAdditional().containsKey(Constants.OMICS_TYPE_FIELD)) {
                        List<String> omicsType = Collections.list(
                                Collections.enumeration(dataset.getAdditional().get(Constants.OMICS_TYPE_FIELD)));
                        datasetSummary.setOmicsType(omicsType);
                    }
                    datasetSummaryList.add(datasetSummary);
            }
            result.setDatasets(datasetSummaryList);
            result.setCount(size);
            return result;
    }


    @ApiOperation(value = "Retrieve the related datasets to one Dataset", position = 1,
            notes = "Retrieve the related datasets to one Dataset")
    @RequestMapping(value = "/getSimilar", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public DataSetResult moreLikeThis(
            @ApiParam(value = "Accession of the Dataset in the resource, e.g : PXD000210")
            @RequestParam(value = "acc", required = true) String acc,
            @ApiParam(value = "Database accession id, e.g : pride")
            @RequestParam(value = "database", required = true) String domain) {

        SimilarResult queryResult = dataWsClient.getSimilarProjects(domain, acc, Constants.MORELIKE_FIELDS);

        DataSetResult result = new DataSetResult();
        List<DatasetSummary> datasetSummaryList = new ArrayList<>();

        Map<String, Map<String, String>> currentIds = new HashMap<>();


        if (queryResult != null && queryResult.getEntries() != null && queryResult.getEntries().length > 0) {

            for (Entry entry: queryResult.getEntries()) {
                if (entry.getId() != null && entry.getSource() != null) {
                    Map<String, String> ids = currentIds.get(entry.getSource());
                    ids = (ids != null) ? ids : new HashMap<>();
                    if (!(entry.getId().equalsIgnoreCase(acc) && entry.getSource().equalsIgnoreCase(domain))) {
                        ids.put(entry.getId(), entry.getScore());
                    }
                    if (!ids.isEmpty()) {
                        currentIds.put(entry.getSource(), ids);
                    }
                }
            }

            for (String currentDomain: currentIds.keySet()) {
                QueryResult datasetResult = dataWsClient.getDatasetsById(
                        currentDomain, Constants.DATASET_DETAIL, currentIds.get(currentDomain).keySet());
                datasetSummaryList.addAll(transformSimilarDatasetSummary(
                        datasetResult, currentDomain, currentIds.get(currentDomain)));
            }

            datasetSummaryList.sort((o1, o2) -> {
                Double value1 = Double.valueOf(o1.getScore());
                Double value2 = Double.valueOf(o2.getScore());
                if (value1 < value2) {
                    return 1;
                } else if (Objects.equals(value1, value2)) {
                    return 0;
                } else {
                    return -1;
                }
            });

            result.setDatasets(datasetSummaryList);
            result.setCount(datasetSummaryList.size());

            return result;
        }

        return null;
    }


    @ApiOperation(value = "Retrieve all file links for a given dataset", position = 1,
            notes = "Retrieve all file links for a given dataset")
    @RequestMapping(value = "/getFileLinks", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public List<String> getFileLinks(
            @ApiParam(value = "Accession of the Dataset in the resource, e.g : PXD000210")
            @RequestParam(value = "acc", required = true) String acc,
            @ApiParam(value = "Database accession id, e.g : pride")
            @RequestParam(value = "database", required = true) String domain) {
        List<String> files = new ArrayList<>();

        String[] fields = {Constants.DATASET_FILE};

        Set<String> currentIds = Collections.singleton(acc);

        QueryResult datasetResult = dataWsClient.getDatasetsById(
                databaseDetailService.retriveAnchorName(domain), fields, currentIds);

        if (datasetResult != null && datasetResult.getEntries() != null && datasetResult.getEntries().length > 0) {
            Entry entry = datasetResult.getEntries()[0];
            String[] fileNames = entry.getFields().get(Constants.DATASET_FILE);
            if (fileNames != null && fileNames.length > 0) {
                for (String fileName: fileNames) {
                    if (fileName != null) {
                        files.add(fileName);
                    }
                }
            }
        }
        return files;
    }

    @ApiOperation(value = "Get dataset by Url", notes = "Retrieve dataset by source url")
    @RequestMapping(value = "/getDatasetByUrl", method = RequestMethod.POST, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public Dataset getDatasetByUrl(
            @ApiParam(value = "Url of the Dataset in the resource, "
                    + "e.g : https://www.ebi.ac.uk/arrayexpress/experiments/E-MTAB-5789")
            @RequestBody() String url) {

        return datasetService.findByFullDatasetLink(url);
    }


    private DatasetDetail getBasicDatasetInfo(DatasetDetail datasetDetail, Dataset argDataset) {
        if (argDataset == null) {
            return datasetDetail;
        }
        Map<String, Set<String>> datesField = argDataset.getDates();
        Map<String, Set<String>> fields = argDataset.getAdditional();
        Map<String, Set<String>> crossFields = argDataset.getCrossReferences();
        Set<String> omicsType = argDataset.getAdditional().get(Constants.OMICS_TYPE_FIELD);
        Set<String> publicationDates = argDataset.getDates().get("publication");

        datasetDetail.setId(argDataset.getAccession());
        datasetDetail.setSource(databaseDetailService.retriveSolrName(argDataset.getDatabase()));
        datasetDetail.setName(argDataset.getName());
        datasetDetail.setDescription(argDataset.getDescription());
        datasetDetail.setClaimable(argDataset.isClaimable());
        datasetDetail.setOmics_type(new ArrayList<>(omicsType));
        datasetDetail.setScores(argDataset.getScores());

        if (publicationDates != null && !publicationDates.isEmpty()) {
            datasetDetail.setPublicationDate(publicationDates.iterator().next());
        }

        Set<String> dataProtocols = fields.get(Constants.DATA_PROTOCOL_FIELD);
        if (dataProtocols != null && dataProtocols.size() > 0) {
            datasetDetail.addProtocols(Constants.DATA_PROTOCOL_FIELD, dataProtocols.toArray(new String[0]));
        }

        Set<String> sampleProtocols = fields.get(Constants.SAMPLE_PROTOCOL_FIELD);
        if (sampleProtocols != null && sampleProtocols.size() > 0) {
            datasetDetail.addProtocols(Constants.SAMPLE_PROTOCOL_FIELD, sampleProtocols.toArray(new String[0]));
        }

        Set<String> fullDatasetLinks = fields.get(Constants.DATASET_LINK_FIELD);
        if (fullDatasetLinks != null && fullDatasetLinks.size() > 0) {
            datasetDetail.setFull_dataset_link(fullDatasetLinks.iterator().next());
        }

        Set<String> diseases = fields.get(Constants.DISEASE_FIELD);
        if (diseases != null && diseases.size() > 0) {
            datasetDetail.setDiseases(diseases.toArray(new String[0]));
        }
        Set<String> viewCountScaled = fields.get(Constants.VIEW_COUNT_SCALED);
        if (viewCountScaled != null && viewCountScaled.size() > 0) {
            datasetDetail.setViewsCountScaled(Double.valueOf(viewCountScaled.iterator().next()));
        }

        Set<String> citationCountScaled = fields.get(Constants.CITATION_COUNT_SCALED);
        if (citationCountScaled != null && citationCountScaled.size() > 0) {
            datasetDetail.setCitationsCountScaled(Double.valueOf(citationCountScaled.iterator().next()));
        }

        Set<String> reanalysisCountScaled = fields.get(Constants.REANALYZED_COUNT_SCALED);
        if (reanalysisCountScaled != null && reanalysisCountScaled.size() > 0) {
            datasetDetail.setReanalysisCountScaled(Double.valueOf(reanalysisCountScaled.iterator().next()));
        }

        Set<String> searchCountScaled = fields.get(Constants.SEARCH_COUNT_SCALED);
        if (searchCountScaled != null && searchCountScaled.size() > 0) {
            datasetDetail.setConnectionsCountScaled(Double.valueOf(searchCountScaled.iterator().next()));
        }

        Set<String> downloadCountScaled = fields.get(Constants.DOWNLOAD_COUNT_SCALED);
        if (downloadCountScaled != null && downloadCountScaled.size() > 0) {
            String downloadValue = downloadCountScaled.iterator().next();
            datasetDetail.setDownloadCountScaled(Double.valueOf(downloadValue.isEmpty() ? "0.0" : downloadValue));
        }

        Set<String> downloadCount = fields.get(Constants.DOWNLOAD_COUNT);
        if (downloadCount != null && downloadCount.size() > 0) {
            String downloadValue = downloadCount.iterator().next();
            datasetDetail.setDownloadCount(Integer.valueOf(downloadValue.isEmpty() ? "0.0" : downloadValue));
        }

        if (datesField != null && datesField.size() > 0) {
            datasetDetail.setDates(datesField);
        }

        Set<String> tissues = fields.get(Constants.TISSUE_FIELD);
        if (tissues != null && tissues.size() > 0) {
            datasetDetail.setTissues(setToArray(tissues, String.class));
        }

        Set<String> instruments = fields.get(Constants.INSTRUMENT_FIELD);

        if (instruments != null && instruments.size() > 0) {
            datasetDetail.setArrayInstruments(setToArray(instruments, String.class));
        }

        Set<String> experimentType = fields.get(Constants.EXPERIMENT_TYPE_FIELD);
        if (experimentType != null && experimentType.size() > 0) {
            datasetDetail.setArrayExperimentType(setToArray(experimentType, String.class));
        }

        Set<String> pubmedids = crossFields.get(Constants.PUBMED_FIELD);
        if ((pubmedids != null) && (pubmedids.size() > 0)) {
            datasetDetail.setArrayPublicationIds(setToArray(pubmedids, String.class));
        }

        Set<String> submitterKeys = fields.get(Constants.SUBMITTER_KEY_FIELD);
        Set<String> curatorKeys = fields.get(Constants.CURATOR_KEY_FIELD);

        if (submitterKeys != null && curatorKeys != null && submitterKeys.size() > 0 && curatorKeys.size() > 0) {
            datasetDetail.setKeywords(setToArray(submitterKeys, String.class), setToArray(curatorKeys, String.class));
        }

        Set<String> organization = fields.get(Constants.ORGANIZATION_FIELD);
        if ((organization != null) && (organization.size() > 0)) {
            datasetDetail.setOrganization(new ArrayList<>(organization));
        }

        Set<String> submitter = fields.get(Constants.SUBMITTER_FIELD);
        if ((submitter != null) && (submitter.size() > 0)) {
            datasetDetail.setSubmitter(submitter);
        }

        Set<String> repositories = fields.get(Constants.REPOSITORY_FIELD);

        if (repositories != null && repositories.size() > 0) {
            datasetDetail.setRepositories(repositories);
        }

        if (argDataset.getScores() != null) {
            Scores scores = argDataset.getScores();
            datasetDetail.setViewsCount(scores.getViewCount());
            datasetDetail.setCitationsCount(scores.getCitationCount());
            datasetDetail.setReanalysisCount(scores.getReanalysisCount());
            datasetDetail.setConnectionsCount(scores.getSearchCount());
        }

        return datasetDetail;
    }

    public DatasetDetail getDatasetInfo(DatasetDetail datasetDetail, Dataset argDataset) {
        if (argDataset == null) {
            return datasetDetail;
        }

        Map<String, Set<String>> fields = argDataset.getAdditional();

        Set<String> submitterMail = fields.get(Constants.SUBMITTER_MAIL_FIELD);
        if ((submitterMail != null) && (submitterMail.size() > 0)) {
            datasetDetail.setSubmitterMail(submitterMail);
        }

        Set<String> submitterEmail = fields.get(Constants.SUBMITTER_EMAIL_FIELD);
        if ((submitterEmail != null) && (submitterEmail.size() > 0)) {
            datasetDetail.setSubmitterMail(submitterEmail);
        }

        Set<String> labhead = fields.get(Constants.LAB_HEAD_FIELD);
        if ((labhead != null) && (labhead.size() > 0)) {
            datasetDetail.setLabHead(labhead);
        }

        Set<String> labHeadMail = fields.get(Constants.LAB_HEAD_MAIL_FIELD);
        if ((labHeadMail != null) && (labHeadMail.size() > 0)) {
            datasetDetail.setLabHeadMail(labHeadMail);
        }

        Set<String> taxonomyIds = argDataset.getCrossReferences().get(Constants.TAXONOMY_FIELD);
        if (taxonomyIds != null && taxonomyIds.size() > 0) {
            ArrayList<String> ids = new ArrayList<>(taxonomyIds);
            QueryResult taxonomies = new QueryResult();

            if (ids.size() > 0) {
                if (ids.size() < 99) {
                    taxonomies = dataWsClient.getDatasetsById(
                            Constants.TAXONOMY_DOMAIN, Constants.TAXONOMY_FIELDS, new HashSet<>(ids));
                } else {
                    int i = 0;
                    while (i + 50 < ids.size()) {
                        List<String> idTemp = ids.subList(i, i + 50);
                        taxonomies.addResults(dataWsClient.getDatasetsById(
                                Constants.TAXONOMY_DOMAIN, Constants.TAXONOMY_FIELDS, new HashSet<>(idTemp)));
                        i = i + 50;
                    }
                    if (i < ids.size()) {
                        List<String> idTemp = ids.subList(i, ids.size());
                        taxonomies.addResults(dataWsClient.getDatasetsById(
                                Constants.TAXONOMY_DOMAIN, Constants.TAXONOMY_FIELDS, new HashSet<>(idTemp)));
                    }
                }
            }

            RepoDatasetMapper.addTaxonomy(datasetDetail, taxonomies);
        }

        //secondary accessions resolved via identifiers collection
        List<String> secondaryAccessionsPlus = new ArrayList<>();
        List<String> secondaryAccession1 = enrichmentService.getAdditionalAccession(argDataset.getAccession());
        if (null != secondaryAccession1) {
            secondaryAccessionsPlus.addAll(secondaryAccession1);
        }
        Set<String> secondaryAccession = fields.get(Constants.SECONDARY_ACCESSION_FIELD);
        Set<String> additionalAccession = fields.get(Constants.ADDITIONAL_ACCESSION_FIELD);

        if (additionalAccession != null && additionalAccession.size() > 0) {
            for (String acc : additionalAccession) {
                if (null == datasetDetail.getSecondary_accession()) {
                    datasetDetail.setSecondary_accession(new HashSet<>());
                }
                datasetDetail.getSecondary_accession().add(acc);
            }
        }
        if ((secondaryAccession != null) && (secondaryAccession.size() > 0)) {
            datasetDetail.setSecondary_accession(secondaryAccession);
            for (String s: secondaryAccession) {
                List<String> secondaryAccession2 = enrichmentService.getAdditionalAccession(s);
                if (null != secondaryAccession2) {
                    secondaryAccessionsPlus.addAll(secondaryAccession2);
                }
            }
        }

        for (String acc : secondaryAccessionsPlus) {
            if (null == datasetDetail.getSecondary_accession()) {
                datasetDetail.setSecondary_accession(new HashSet<>());
            }
            datasetDetail.getSecondary_accession().add(acc);
        }

        return datasetDetail;
    }

    public <T> T[] setToArray(Set<T> argSet, Class<T> type) {
        return argSet.toArray((T[]) Array.newInstance(type, argSet.size()));
    }

    @ApiOperation(value = "Retrieve all similar dataset based on pubmed id",
            position = 1, notes = "Retrieve all datasets which have same pubmed id")
    @RequestMapping(value = "/getSimilarByPubmed", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public List<Dataset> getSimilarDatasets(
            @ApiParam(value = "Pubmed Id of the Dataset in the resource, e.g : 16585740")
            @RequestParam(value = "pubmed", required = true) String pubmed) {
        return datasetService.getSimilarByPubmed(pubmed);
    }

    @ApiOperation(value = "Retrieve merge candidates", notes = "Retrieve merge candidates")
    @RequestMapping(value = "/getMergeCandidates", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public List<MergeCandidate> getMergeCandidates(
            @ApiParam(value = "The starting point for the search, e.g: 0")
            @RequestParam(value = "start", required = false, defaultValue = "0") int start,
            @ApiParam(value = "The number of records to be retrieved, e.g: maximum 100")
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            @RequestHeader HttpHeaders headers) {
        userPermissionService.hasRole(Role.ADMIN, headers);
        return datasetService.getMergeCandidates(start, size);
    }

    @ApiOperation(value = "Merge datasets", notes = "Merge datasets")
    @RequestMapping(value = "/merge", method = RequestMethod.POST, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public void mergeDatasets(@RequestBody MergeCandidate mergeCandidate, @RequestHeader HttpHeaders headers) {
        userPermissionService.hasRole(Role.ADMIN, headers);
        datasetService.mergeDatasets(mergeCandidate);
    }

    @ApiOperation(value = "Skipping merge datasets", notes = "Skip merge datasets")
    @RequestMapping(value = "/skipMerge", method = RequestMethod.POST, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public void skipDatasets(@RequestBody MergeCandidate mergeCandidate, @RequestHeader HttpHeaders headers) {
        userPermissionService.hasRole(Role.ADMIN, headers);
        datasetService.skipMerge(mergeCandidate);
    }

    @ApiOperation(value = "Multiomics merging datasets", notes = "Multiomics merging datasets")
    @RequestMapping(value = "/multiomicsMerge", method = RequestMethod.POST, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public void multiomicsMergeDatasets(@RequestBody MergeCandidate candidate, @RequestHeader HttpHeaders headers) {
        userPermissionService.hasRole(Role.ADMIN, headers);
        datasetService.addMultiomics(candidate);
    }

    @ApiOperation(value = "Retrieve merge candidate counts", notes = "Retrieve merge candidate counts")
    @RequestMapping(value = "/getMergeCandidateCount", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public Integer getMergeCandidateCount(@RequestHeader HttpHeaders headers) {
        userPermissionService.hasRole(Role.ADMIN, headers);
        return datasetService.getMergeCandidateCount();
    }

    @ApiOperation(value = "Retrieve all dataset counts by database", position = 1,
            notes = "Retrieve all datasets count by database")
    @RequestMapping(value = "/getDbDatasetCount", method = RequestMethod.GET,
            produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public List<DbDatasetCount> getDbDatasetsCount() {
        return datasetService.getDbDatasetsCount();
    }


    @ApiOperation(value = "Unmerge datasets", notes = "Un-merge datasets")
    @RequestMapping(value = "/unmerge", method = RequestMethod.POST, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public void unMergeDatasets(@RequestBody List<UnMergeDatasets> mergeCandidate, @RequestHeader HttpHeaders headers) {
        userPermissionService.hasRole(Role.ADMIN, headers);
        unMergeDatasetService.unmergeDataset(mergeCandidate);
    }

    @ApiOperation(value = "Get all merged datasets", notes = "Get all merged datasets")
    @RequestMapping(value = "/getAllmerged", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    @ResponseBody
    public List<UnMergeDatasets> getAllMergedDatasets(@RequestHeader HttpHeaders headers) {
        userPermissionService.hasRole(Role.ADMIN, headers);
        return unMergeDatasetService.findAll();
    }

    @ApiOperation(value = "Get all datasets", notes = "Get all datasets")
    @RequestMapping(value = "/getAll", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Stream<Dataset> getAllDatasets() {
        return datasetService.getAllData();
    }

    @ApiOperation(value = "Get all datasets by pages", notes = "Get all datasets by pages")
    @RequestMapping(value = "/getDatasetPage", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Page<Dataset> getAllDatasetPage(
            @ApiParam(value = "The starting point for the search, e.g: 0")
            @RequestParam(value = "start", required = false, defaultValue = "0") int start,
            @ApiParam(value = "The number of records to be retrieved, e.g: maximum 100")
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return datasetService.getDatasetPage(start, size);
    }
}

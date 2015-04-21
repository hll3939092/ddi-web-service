package uk.ac.ebi.ddi.ws.util;

import uk.ac.ebi.ddi.ebe.ws.dao.utils.Constans;

/**
 * @author Yasset Perez-Riverol ypriverol
 */
public class Constants {

    public static final String ENTRY_COUNT           = "Number of entries";

    public static final String TAXONOMY_FIELD        = "TAXONOMY";

    public static final String MAIN_DOMAIN           = "omics";

    public static final String REPOSITORY_TAG        = "Repositories";

    public static final String DATASET_TAGS          = "Datasets";

    public static final String CONTRIBUTORS_TAG      = "Contributors";

    public static final String SPECIES_TAG           = "Different Species";

    public static final String MANUSCRIPT_TAG        = "Manuscripts";

    public static final String TISSUE_FIELD          = "tissue" ;

    public static final String OMICS_TYPE_FIELD      = "omics_type";

    public static final String DISEASE_FIELD         = "disease";

    public static String DESCRIPTION_FIELD           = "description";

    public static String NAME_FIELD                  = "name";

    public static String SUBMITTER_KEY_FIELD         = "submitter_keywords";

    public static String CURATOR_KEY_FIELD           = "curator_keywords";

    public static String PUB_DATE_FIELD              = "publication_date";

    public static String[] DATASET_SUMMARY           = {Constants.DESCRIPTION_FIELD,
                                                        Constants.NAME_FIELD,
                                                        Constants.SUBMITTER_KEY_FIELD,Constants.CURATOR_KEY_FIELD,
                                                        Constants.PUB_DATE_FIELD,
                                                        Constants.TAXONOMY_FIELD};

    public static String[] EXCLUSION_WORDS           = {"1","2","3","4","5","6","7","8","9","a","b","c","d","e","f","g","h",
            "i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z",
            "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is", "it",
            "no", "not", "of", "on", "or", "such", "that", "the", "their", "then", "there", "these", "they", "this", "to", "was", "will", "with",
            "able","about","across","after","all","almost","also","am","among","an","and","any","are","as","at","be","because","been","can","could","dear","did",
            "do","does","either","else","ever","every","for","from","get","got","had","has","have","he","her","hers","him","how","however","i","in","into",
            "its","just","least","let","like","likely","may","me","might","most","must","neither","no","nor","not","of","off",
            "often","on","only","or","other","our","own","rather","should","since","so","some","than","that","the","their","them," +
            "then","there","these","they","this","tis","to","too","us","was","we","were","what","when","where","which","while",
            "who","whom","why","will","with","would","yet","you","your",
            "protein", "proteomics", "proteomic", "proteome", "proteomes", "mass", "proteins", "lc", "ms", "based", "from", "using", "during", "LC-MS", "LC-MS/MS","reveals","as","non","data"};



}
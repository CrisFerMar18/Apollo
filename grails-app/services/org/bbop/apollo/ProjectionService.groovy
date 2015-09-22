package org.bbop.apollo

import grails.converters.JSON
import grails.transaction.NotTransactional
import grails.transaction.Transactional
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.projection.Coordinate
import org.bbop.apollo.projection.DiscontinuousProjection
import org.bbop.apollo.projection.ProjectionInterface
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject

@Transactional(readOnly = true)
class ProjectionService {

    // TODO: move to database as JSON
    // track, sequence, projection
    // TODO: should also include organism at some point as well
    // TODO: just turn this into a cache file
    private Map<String, Map<String, ProjectionInterface>> projectionMap = new HashMap<>()

    // TODO: should do an actual lookup / query in cache and DB
    @NotTransactional
    Boolean hasProjection(Organism organism, String trackName) {
        return projectionMap.size() > 0
    }

    // TODO: do re-lookup
    /**
     *
     * @param organism
     * @param trackName TODO: this is the REFERENCE track!! .. might be too specific
     * @param sequenceName
     * @return
     */
    @NotTransactional
    ProjectionInterface getProjection(Organism organism, String trackName, String sequenceName) {
        return projectionMap ? projectionMap.values()?.iterator()?.next()?.get(sequenceName) : null
    }


    @NotTransactional
    String getTrackName(String fileName) {
        String[] tokens = fileName.split("/")
        return tokens[tokens.length - 3]
    }

    @NotTransactional
    String getSequenceName(String fileName) {
        String[] tokens = fileName.split("/")
        return tokens[tokens.length - 2]
    }

    // TODO: do re-lookup
    def createTranscriptProjection(Organism organism, JSONArray tracksArray, Integer padding = 0) {
        // TODO: this is only here for debugging . .
        projectionMap.clear()
        long startTime = System.currentTimeMillis()
        for (int i = 0; i < tracksArray.size(); i++) {
            JSONObject trackObject = tracksArray.getJSONObject(i)
            if (trackObject.containsKey("OGS") && trackObject.getBoolean("OGS") && !projectionMap.containsKey(trackObject.keys())) {
                println "tring to generate projection for ${trackObject.key}"
                String jbrowseDirectory = organism.directory + "/tracks/" + trackObject.key
                File trackDirectory = new File(jbrowseDirectory)
                println "track directory ${trackDirectory.absolutePath}"

                File[] files = FileUtils.listFiles(trackDirectory, FileFilterUtils.nameFileFilter("trackData.json"), TrueFileFilter.INSTANCE)

                println "# of files ${files.length}"

                Map<String, ProjectionInterface> sequenceProjectionMap = new HashMap<>()

                for (File trackDataFile in files) {
//                    println "file ${trackDataFile.absolutePath}"

//                    String sequenceFileName = trackDataFile.absolutePath.substring(trackDirectory.absolutePath.length(),trackDataFile.absolutePath.length()-"trackData.json".length()).replaceAll("/","")
                    String sequenceFileName = getSequenceName(trackDataFile.absolutePath)

//                    println "sequencefileName [${sequenceFileName}]"

                    JSONObject referenceJsonObject = new JSONObject(trackDataFile.text)

                    // TODO: interpret the format properly
                    DiscontinuousProjection discontinuousProjection = new DiscontinuousProjection()
                    JSONArray coordinateReferenceJsonArray = referenceJsonObject.getJSONObject(FeatureStringEnum.INTERVALS.value).getJSONArray(FeatureStringEnum.NCLIST.value)
                    for (int coordIndex = 0; coordIndex < coordinateReferenceJsonArray.size(); ++coordIndex) {
                        JSONArray coordinate = coordinateReferenceJsonArray.getJSONArray(coordIndex)
                        // TODO: use enums to better track format
                        if (coordinate.getInt(0) == 4) {
                            // projecess the file lf-${coordIndex} instead
                            File chunkFile = new File(trackDataFile.parent + "/lf-${coordIndex + 1}.json")
                            JSONArray chunkReferenceJsonArray = new JSONArray(chunkFile.text)

                            for (int chunkArrayIndex = 0; chunkArrayIndex < chunkReferenceJsonArray.size(); ++chunkArrayIndex) {
                                JSONArray chunkArrayCoordinate = chunkReferenceJsonArray.getJSONArray(chunkArrayIndex)
                                discontinuousProjection.addInterval(chunkArrayCoordinate.getInt(1), chunkArrayCoordinate.getInt(2) , padding)
                            }

                        } else {
                            discontinuousProjection.addInterval(coordinate.getInt(1), coordinate.getInt(2),padding)
                        }
                    }

                    sequenceProjectionMap.put(sequenceFileName, discontinuousProjection)
                }

                println "final size: ${trackObject.key} -> ${sequenceProjectionMap.size()}"

                projectionMap.put(trackObject.key, sequenceProjectionMap)
            }
        }
        println "total time ${System.currentTimeMillis() - startTime}"
    }

    /**
     *
     * TODO: do re-lookup
     *
     * If in trackList . . or lf-x
     *
     * If type is "mRNA", search for any subarrays including sublist in array
     * If type is "other transcript?", ??
     * If type is "exon", add Interval for min, max, else ginreo
     *
     * The "type" is listed in 0-> column 9
     * The "type" is listed in 1-> column 7
     * The "type" is listed in 2-> column 6
     * The "type" is listed in 3-> column 6
     * The "type" is listed in 4-> column ?
     *
     * // and in the sublist as well (typicallly column 11 of an mRNA . . prob for overlap
     *
     * @param organism
     * @param tracksArray
     * @return
     */
    def createExonLevelProjection(Organism organism, JSONArray tracksArray, Integer padding = 0) {
        // TODO: this is only here for debugging . .
//        projectionMap.clear()
        long startTime = System.currentTimeMillis()
        for (int i = 0; i < tracksArray.size(); i++) {
            JSONObject trackObject = tracksArray.getJSONObject(i)
            if (trackObject.containsKey("OGS") && trackObject.getBoolean("OGS") && !projectionMap.containsKey(trackObject.key)) {
                println "tring to generate projection for ${trackObject.key}"
                String jbrowseDirectory = organism.directory + "/tracks/" + trackObject.key
                File trackDirectory = new File(jbrowseDirectory)
                println "track directory ${trackDirectory.absolutePath}"

                File[] files = FileUtils.listFiles(trackDirectory, FileFilterUtils.nameFileFilter("trackData.json"), TrueFileFilter.INSTANCE)

                println "# of files ${files.length}"

                Map<String, ProjectionInterface> sequenceProjectionMap = new HashMap<>()

                for (File trackDataFile in files) {
//                    String sequenceFileName = trackDataFile.absolutePath.substring(trackDirectory.absolutePath.length(),trackDataFile.absolutePath.length()-"trackData.json".length()).replaceAll("/","")
                    String sequenceFileName = getSequenceName(trackDataFile.absolutePath)

                    JSONObject referenceJsonObject = new JSONObject(trackDataFile.text)
                    if(sequenceFileName.contains("1.10")){
                        println "traying to create a sequence for ${sequenceFileName} "
                    }

                    // TODO: interpret the format properly
                    DiscontinuousProjection discontinuousProjection = new DiscontinuousProjection()
                    JSONArray coordinateReferenceJsonArray = referenceJsonObject.getJSONObject(FeatureStringEnum.INTERVALS.value).getJSONArray(FeatureStringEnum.NCLIST.value)
                    for (int coordIndex = 0; coordIndex < coordinateReferenceJsonArray.size(); ++coordIndex) {

                        // TODO: this needs to be recursive
                        JSONArray coordinate = coordinateReferenceJsonArray.getJSONArray(coordIndex)

                        if (coordinate.getInt(0) == 4) {
                            // projecess the file lf-${coordIndex} instead
                            File chunkFile = new File(trackDataFile.parent + "/lf-${coordIndex + 1}.json")
                            JSONArray chunkReferenceJsonArray = new JSONArray(chunkFile.text)

                            for (int chunkArrayIndex = 0; chunkArrayIndex < chunkReferenceJsonArray.size(); ++chunkArrayIndex) {
                                JSONArray chunkArrayCoordinate = chunkReferenceJsonArray.getJSONArray(chunkArrayIndex)
                                processHighLevelArray(discontinuousProjection, chunkArrayCoordinate,padding)
                            }

                        } else {
                            processHighLevelArray(discontinuousProjection, coordinate,padding)
                        }
                    }

//                    println "# of entries: ${discontinuousProjection.minMap.size()}"

                    if(sequenceFileName.contains("1.10")){
                        println "putting map ${sequenceFileName} into ${discontinuousProjection.size()}"
                    }
                    sequenceProjectionMap.put(sequenceFileName, discontinuousProjection)
                }

                println "final size: ${trackObject.key} -> ${sequenceProjectionMap.size()}"

                projectionMap.put(trackObject.key, sequenceProjectionMap)
            }
        }
        println "total time ${System.currentTimeMillis() - startTime}"
    }

    def processHighLevelArray(DiscontinuousProjection discontinuousProjection, JSONArray coordinate,Integer padding) {
//                        // TODO: use enums to better track format
        int classType = coordinate.getInt(0)
        log.debug "processing high level array  ${coordinate as JSON}"
        String featureType
        switch (classType) {
            case 0:
                featureType = coordinate.getString(9)
                // process array in 10
                processExonArray(discontinuousProjection, coordinate.getJSONArray(10),padding)
                // process sublist if 11 exists
                break
            case 1:
                featureType = coordinate.getString(7)
                println "1 - doing nothing for this . . . no subarray? ${coordinate as JSON}"
                // no subarrays
                break
            case 2:
            case 3:
                featureType = coordinate.getString(6)
                println "2/3 - doing nothing for this . . . no subarray? ${coordinate as JSON}"
                // process array in 10
                // process sublist if 11 exists
                break
            case 4:
                println "not sure how to handle case 4 ${coordinate as JSON}"
                // ignore .  . . not an exon
                break
        }

    }

    def processExonArray(DiscontinuousProjection discontinuousProjection, JSONArray coordinate, Integer padding) {
        log.debug "processing exon array ${coordinate as  JSON}"
        def classType = coordinate.get(0)

        // then we assume that the rest are arrays if the first are . . and process them accordingly
        if(classType instanceof JSONArray){
            for(int i = 0 ; i < coordinate.size() ; i++){
                log.debug "subarray ${coordinate.get(i) as JSON}"
                processExonArray(discontinuousProjection,coordinate.getJSONArray(i),padding)
            }
            return
        }
        else{
            // integer
            classType = coordinate.getInt(0)
        }
        String featureType
        switch (classType) {
            case 0:
                log.debug "not sure if this will work . . check! ${coordinate.size()} > 9"
                featureType = coordinate.getString(9)
                if (coordinate.size() >= 10) {
                    processExonArray(discontinuousProjection, coordinate.getJSONArray(10),padding)
                }
                if (coordinate.size() >= 11) {
                    JSONObject sublist = coordinate.getJSONObject(11)
                    processHighLevelArray(discontinuousProjection, sublist.getJSONArray("Sublist"),padding)
                }
                break
            case 1:
            case 2:
                featureType = coordinate.getString(7)
                break
            case 3:
                featureType = coordinate.getString(6)
                break
            case 4:
                println "not sure how to handle case 4 ${coordinate as JSON}"
                break
        }

        // TODO: or repeat region?
        if (featureType && featureType == "exon") {
            discontinuousProjection.addInterval(coordinate.getInt(1), coordinate.getInt(2),padding)
        }

//        }

    }

    /**
     * Anything in this space is assumed to be visible
     * @param sequence
     * @param referenceTrackName
     * @param inputFeaturesArray
     * @return
     */
    @Transactional(readOnly = true)
    JSONArray projectFeatures(Sequence sequence, String referenceTrackName, JSONArray inputFeaturesArray,Boolean reverseProjection) {
        DiscontinuousProjection projection = (DiscontinuousProjection) getProjection(sequence.organism, referenceTrackName, sequence.name)
        println "trying to convert ${inputFeaturesArray as JSON}"
        if(projection){
            // process location . . .
            projectFeaturesArray(inputFeaturesArray,projection,reverseProjection)
            println "converted ${inputFeaturesArray as JSON}"
        }
        return inputFeaturesArray
    }

    @NotTransactional
    JSONArray projectFeaturesArray(JSONArray inputFeaturesArray,DiscontinuousProjection projection,Boolean reverseProjection) {
        for(int i = 0 ; i < inputFeaturesArray.size() ;i++){
            JSONObject inputFeature = inputFeaturesArray.getJSONObject(i)
            projectFeature(inputFeature,projection,reverseProjection)
            JSONArray childFeatures = inputFeature.getJSONArray(FeatureStringEnum.CHILDREN.value)
            if(childFeatures){
                projectFeaturesArray(childFeatures,projection,reverseProjection)
            }
        }
        return inputFeaturesArray
    }

    @NotTransactional
    JSONObject projectFeature(JSONObject inputFeature,DiscontinuousProjection projection,Boolean reverseProjection) {
        JSONObject locationObject = inputFeature.getJSONObject(FeatureStringEnum.LOCATION.value)
        println "loaction object ${locationObject as JSON}"
        Integer fmin = locationObject.getInt(FeatureStringEnum.FMIN.value)
        Integer fmax = locationObject.getInt(FeatureStringEnum.FMAX.value)
        Coordinate projectedCoordinate = reverseProjection ? projection.projectReverseCoordinate(fmin,fmax) : projection.projectCoordinate(fmin,fmax)
        println "projected coordainte ${projectedCoordinate}"
        locationObject.put(FeatureStringEnum.FMIN.value,projectedCoordinate.min)
        locationObject.put(FeatureStringEnum.FMAX.value,projectedCoordinate.max)
        return inputFeature
    }
}

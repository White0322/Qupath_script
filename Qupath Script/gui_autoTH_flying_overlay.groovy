/* Update flying pane:  GUI for using Auto-Threshold methods 
 * 
 * This is the update for this script: https://github.com/iviecomarti/GUI_AutoTH_QuPath/blob/main/gui/gui_AutoTH_QuPath.groovy
 * 
 * Updates: 
 * - Thanks to @EP.Zindy  some deprecated methods have been removed
 * -The main function to create the output is splited into three, to make overlay functions easier.
 * -Preview and Stop Preview options added. Thanks @yau-lim for sharing the code
 * 
 *  
 * @author Isaac Vieco-Martí
 *  
 */


import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.stage.Stage
import qupath.lib.gui.QuPathGUI
import qupath.fx.dialogs.Dialogs
import qupath.lib.gui.tools.GuiTools
import qupath.fx.utils.GridPaneUtils
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathObjectTools
import qupath.lib.objects.PathObjects
import qupath.lib.objects.classes.PathClass
import qupath.lib.roi.GeometryTools
import javafx.collections.FXCollections
import qupath.fx.utils.FXUtils
import qupath.lib.plugins.parameters.ParameterList;
import qupath.process.gui.commands.ml.ClassificationResolution;
import qupath.lib.images.ImageData;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Spinner;
import ij.IJ;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;

//GUI//

Platform.runLater { buildStage().show()}

Stage buildStage() {
    def qupath = QuPathGUI.getInstance()
    
    
    ////////////////////////////
    // Auto-Threshold Methods///
    ////////////////////////////
    def options = ["Huang", "Otsu","Triangle","Intermodes", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Mean", "MinError", "Minimum", "Moments",  "Percentile", "RenyiEntropy", "Shanbhag",  "Yen"]
    def pane = new GridPane()
    def combo = new ComboBox<>(FXCollections.observableArrayList(options))
    combo.getSelectionModel().selectFirst()
    combo.setTooltip(new Tooltip("Choose the auto threshold"))
    def labelCombo = new Label("Auto Threshold method")
    labelCombo.setLabelFor(combo)
    

    int row = 0
    pane.add(labelCombo, 0, row, 1, 1)
    pane.add(combo, 1, row, 1, 1)
   
   
    //Select the channel
   
    def channelOptions = []
    if(getCurrentImageData().getImageType().toString().contains("H&E")) {
        channelOptions = ["Red","Green","Blue","Hematoxylin","Eosin","Residual","Average"]
       
    }else if(getCurrentImageData().getImageType().toString().contains("H-DAB")) {
       channelOptions = ["Red","Green","Blue","Hematoxylin","DAB","Residual","Average"]
       
    } else if (getCurrentImageData().getImageType().toString().contains("Fluorescence")) {
       
       def avg = "Average"
       channelOptions = getCurrentServer().getMetadata().getChannels().collect { c -> c.name }
       
       channelOptions << avg

       
    }
   
   
    def comboChannel = new ComboBox<>(FXCollections.observableArrayList(channelOptions))
    comboChannel.getSelectionModel().selectFirst()
    comboChannel.setTooltip(new Tooltip("Select the channel"))
    def labelcomboChannel = new Label("Channel")
    //labelCombo.setLabelFor(combo)
   
    row++
   
    pane.add(labelcomboChannel, 0, row, 1, 1)
    pane.add(comboChannel, 1, row, 1, 1)
    
    
    //Floor Value
    def floorValueSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 255.0, 0.0, 1.0));
    floorValueSpinner.setEditable(true);
    FXUtils.restrictTextFieldInputToNumber(floorValueSpinner.getEditor(), true);
    floorValueSpinner.setTooltip(new Tooltip("Set a threshold floor value in case auto threshold is too low. Set 0 to disable"))
    def labelfloorValueSpinner = new Label("Floor value")
    
    row++
    pane.add(labelfloorValueSpinner, 0, row, 1, 1)
    pane.add(floorValueSpinner, 1, row, 1, 1)
    
    
    ////////////////////
    //IMAGE DOWNSAMPLE//
    ////////////////////
    
    row++
    row++
    downsampleTitle = new Label("Image Downsample")
    downsampleTitle.setStyle("-fx-font-weight: bold")
    pane.add(downsampleTitle, 0,row, 1, 1)
   
    //Viewer dependent downsample
    def cbAutoDownsample= new CheckBox("Auto-Downsample")
    cbAutoDownsample.setSelected(false)
    cbAutoDownsample.setTooltip(new Tooltip("If checked, the downsample will be adapted from the current viewer."))

    row++;
    pane.add(cbAutoDownsample, 0, row, 2, 1)
       
   
   
    //Manual downsample
    def optionsDownsample = [1,2,4,8,16,32,64]
    def comboResolutions = new ComboBox<>(FXCollections.observableArrayList(optionsDownsample));
    comboResolutions.setTooltip(new Tooltip("If Auto-Downsample is not checked \n 1:Full, 2:Very high, 4:High, 8:Moderate, 16:Low, 32:Very low, 64:Extremely low"))
    comboResolutions.getSelectionModel().selectFirst()
    
    pane.add(comboResolutions, 1, row, 1, 1)
    
    
    
    ///////////////////////
    //CLASSIFIER SETTINGS//
    ///////////////////////
   row++ 
   row++
   classificationTittle = new Label("Classifier settings")
   classificationTittle.setStyle("-fx-font-weight: bold")
   pane.add(classificationTittle, 0,row, 1, 1)
   
   
   //classifier downsample
    def labelResolutionsClassifier = new Label("Classifier Downsample")
    def optionsDownsampleClassifier = [1,2,4,8,16,32,64]
    def comboResolutionsClassifier = new ComboBox<>(FXCollections.observableArrayList(optionsDownsampleClassifier));
    comboResolutionsClassifier.setTooltip(new Tooltip("If Auto-Downsample is not checked \n 1:Full, 2:Very high, 4:High, 8:Moderate, 16:Low, 32:Very low, 64:Extremely low"))
    comboResolutionsClassifier.getSelectionModel().selectFirst()
   
   
    row++
    pane.add(labelResolutionsClassifier, 0, row, 1, 1)
    pane.add(comboResolutionsClassifier, 1, row, 1, 1)
    
    
   //Prefilter Options:
    prefilterOptions = ["Gaussian", "Laplacian", "Erosion","Dilation","Opening","Closing","Gradient Magnitude","Weighted_STD"]
    def comboPrefilter = new ComboBox<>(FXCollections.observableArrayList(prefilterOptions))
    comboPrefilter.getSelectionModel().selectFirst()
    def labelPrefilter = new Label("Classifier Prefilter")
    comboPrefilter.setTooltip(new Tooltip("Blurring for pixel classifier (not used in calculation of threshold)"))
    
    row++
    pane.add(labelPrefilter, 0, row, 1, 1)
    pane.add(comboPrefilter, 1, row, 1, 1)
    
    //Smoothing sigma
    def sigmaSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 16.0, 0.0, 0.5));
    sigmaSpinner.setEditable(true);
    FXUtils.restrictTextFieldInputToNumber(sigmaSpinner.getEditor(), true);
    def labelSigmaSpinner = new Label("Smoothing sigma")
    row++
    pane.add(labelSigmaSpinner, 0, row, 1, 1)
    pane.add(sigmaSpinner, 1, row, 1, 1)
   
   //Above TH
    def comboAvobe = new ComboBox<PathClass>(qupath.getAvailablePathClasses())
    comboAvobe.getSelectionModel().selectFirst()
    comboAvobe.setTooltip(new Tooltip("Above Threshold Classification"))
    def labelComboAvobe = new Label("Above")
    labelComboAvobe.setLabelFor(comboAvobe)
    
    //Below TH
    def comboBelow = new ComboBox<PathClass>(qupath.getAvailablePathClasses())
    comboBelow.getSelectionModel().selectFirst()
    comboBelow.setTooltip(new Tooltip("Below Threshold Classification"))
    def labelComboBelow = new Label("Below")
    labelComboBelow.setLabelFor(comboBelow)
    
    row++
    pane.add(labelComboAvobe, 0, row, 1, 1)
    pane.add(comboAvobe, 1, row, 1, 1)
    row++
    pane.add(labelComboBelow, 0, row, 1, 1)
    pane.add(comboBelow, 1, row, 1, 1)
    
    
    
    
     ////////////////////////////////////
    ///////////PREVIEW/////////////////
    ///////////////////////////////////
    
    
   row++
   row++
   postTittle = new Label("Preview Classifier")
   postTittle.setStyle("-fx-font-weight: bold")
   pane.add(postTittle, 0,row, 1, 1)
    
    
    
    //Start preview
    
     def btnPreview = new Button("Preview")
    btnPreview.setTooltip(new Tooltip("Preview with the current settings"))
    btnPreview.setOnAction {e ->
        def hierarchy = qupath.getViewer()?.getHierarchy()
        
        
               
        def objects =[]
        
        getSelectedObjects().forEach {
           objects << it 
        }
   
        
        if (objects.isEmpty()) {
            lastParentObjects.clear()
            lastAnnotation = null
            Dialogs.showErrorMessage("Create detection",
                    "Please draw some objects")
            return
        }
        
              
        //Downsample of the img
        def downsampleAT = downsampleDecision(cbAutoDownsample,comboResolutions)
      
                           
           
        //We apply the function to create classifier. 
        classifierPreview= classifierCreation(objects[0],  comboChannel.getValue(), downsampleAT, combo.getValue(), floorValueSpinner.getValue(), comboResolutionsClassifier.getValue(),comboPrefilter.getValue(),sigmaSpinner.getValue(), comboAvobe.getValue(), comboBelow.getValue())
       
       //here we take the 0th element since the function returns the threshold and the classifier
        previewSegmentation(classifierPreview[0])
       
    }
   
    
    
    //Stop preview
     def btnStopPreview = new Button("StopPreview")
    btnStopPreview.setTooltip(new Tooltip("Create the output for the selected objects"))
    btnStopPreview.setOnAction {e ->
        
        stopPreviewSegmentation()
       
    }
    
    row++
    row++
    pane.add(btnPreview, 0, row, 1, 1)
    pane.add(btnStopPreview, 1, row, 1, 1)
    
    
    
    
    
    
    ///////////////////
    //POST-PROCESSING//
    ///////////////////
    
   row++
   row++
   postTittle = new Label("Post-processing settings")
   postTittle.setStyle("-fx-font-weight: bold")
   pane.add(postTittle, 0,row, 1, 1)
    
    
    //minvalue
    def minValueSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 100000.0, 0.0, 0.5));
    minValueSpinner.setEditable(true);
    FXUtils.restrictTextFieldInputToNumber(minValueSpinner.getEditor(), true);
    def labelMinValueSpinner = new Label("Min area microns^2 ")
    
    row++
    pane.add(labelMinValueSpinner, 0, row, 1, 1)
    pane.add(minValueSpinner, 1, row, 1, 1)
    
    //min holes
    def minHoleSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 100000.0, 0.0, 0.5));
    minHoleSpinner.setEditable(true);
    FXUtils.restrictTextFieldInputToNumber(minHoleSpinner.getEditor(), true);
    def labelminHoleSpinner = new Label("Min hole area microns^2 ")
    
    row++
    pane.add(labelminHoleSpinner, 0, row, 1, 1)
    pane.add(minHoleSpinner, 1, row, 1, 1)
    
    
    //SPLIT
    def cbSplit= new CheckBox("Split Objects")
    cbSplit.setSelected(false)
    cbSplit.setTooltip(new Tooltip("Split Objects."))

    row++;
    pane.add(cbSplit, 0, row, 1, 1)
    
    //Delete exisiting
    def cbDeleteActual= new CheckBox("Delete Objects Inside")
    cbDeleteActual.setSelected(false)
    cbDeleteActual.setTooltip(new Tooltip("Delete objects inside the actual annotations"))

    
    pane.add(cbDeleteActual, 1, row, 1, 1)
    
    
    
    //Include ignored
    def cbIncludeIgnored= new CheckBox("Include ignored")
    cbIncludeIgnored.setSelected(false)
    cbIncludeIgnored.setTooltip(new Tooltip("Create objects for ignored classes"))

    row++
    pane.add(cbIncludeIgnored, 0, row, 1, 1)
    
    //Select new
    def cbSelectNew= new CheckBox("Select New Objects")
    cbSelectNew.setSelected(false)
    cbSelectNew.setTooltip(new Tooltip("Select the new objects"))

    
    pane.add(cbSelectNew, 1, row, 1, 1)
    
    //Delete parent
    def cbDeleteParent= new CheckBox("Delete Parent")
    cbDeleteParent.setSelected(false)
    cbDeleteParent.setTooltip(new Tooltip("If checked, the original annotations are deleted."))

    row++;
    pane.add(cbDeleteParent, 0, row, 1, 1)
    

   ////////////////////
   //OUTPUT SELECTION//
   ////////////////////
   row++
   row++
   row++
   actionsTittle = new Label("Output")
   actionsTittle.setStyle("-fx-font-weight: bold")
   pane.add(actionsTittle, 0,row, 1, 1)
   
   
   
   //output type selection
   //AutoTH Methods
    def optionsOutput = ["Annotation", "Detection", "Measurement", "Threshold value"]
    def comboOutput = new ComboBox<>(FXCollections.observableArrayList(optionsOutput))
    comboOutput.getSelectionModel().selectFirst()
    comboOutput.setTooltip(new Tooltip("Choose the desired type of output"))
    def labelComboOutput = new Label("New Object")
    labelComboOutput.setLabelFor(comboOutput)
    

    row++
    pane.add(labelComboOutput, 0, row, 1, 1)
    pane.add(comboOutput, 1, row, 1, 1)
    
    

    
    
    
    ////////////////////
   //RUNNING OPTION 1//
   ////////////////////
    
    row++
    row++
    row++
    runningTittle1 = new Label("Running Option 1: Run For Parent Class")
    runningTittle1.setStyle("-fx-font-weight: bold")
    pane.add(runningTittle1, 0,row, 2, 1)
   

   
   //selectParent classificaiton
    
    def comboParent = new ComboBox<PathClass>(qupath.getAvailablePathClasses())
    comboParent.getSelectionModel().selectFirst()
    comboParent.setTooltip(new Tooltip("Choose parent classification"))
    def labelcomboParent = new Label("Parent Classification")
    labelcomboParent.setLabelFor(comboParent)
    row++
   
    pane.add(labelcomboParent, 0, row, 1, 1)
    pane.add(comboParent, 1, row, 1, 1)
    
   
   row++
   row++
   
   //Button 1
   
   
    def btnRunParents = new Button("Run for Parents")
    btnRunParents.setTooltip(new Tooltip("Create the output for the parents with a calssification"))
   
    btnRunParents.setOnAction {e ->
        def hierarchy = qupath.getViewer()?.getHierarchy()
        
        //funciton to decide at the end, basically gets the objects with the selected class
        def objects = decisionParent(comboParent.getValue().toString())
        
        if (objects.isEmpty()) {
            lastParentObjects.clear()
            lastAnnotation = null
            Dialogs.showErrorMessage("Create detection",
                    "Please draw some objects")
            return
        }
        
              
        //Downsample of the img
        def downsampleAT = downsampleDecision(cbAutoDownsample,comboResolutions)
      
        //Post processing selection. function at the end. 
        postProcessingOptions = postProcessingSelection(cbSplit,cbDeleteActual,cbIncludeIgnored,cbSelectNew)
              
        
        for(object in objects) {
          
          selectObjects(object)
          
          classifierPX= classifierCreation(object,  comboChannel.getValue(), downsampleAT, combo.getValue(), floorValueSpinner.getValue(), comboResolutionsClassifier.getValue(),comboPrefilter.getValue(),sigmaSpinner.getValue(), comboAvobe.getValue(), comboBelow.getValue())
          
          actionRunClassifier(object,combo.getValue(),classifierPX[0],classifierPX[1],comboOutput.getValue(),minValueSpinner.getValue(), minHoleSpinner.getValue(), postProcessingOptions.join(','),comboAvobe.getValue(), comboBelow.getValue())
        }
        
        //Delete parents
        
        if(cbDeleteParent.isSelected()== true) {
           removeObjects(objects, true) 
        }
                                       
       
    }
    
    row++;
    pane.add(btnRunParents, 0, row, 2, 1)
    

   ////////////////////
   //RUNNING OPTION 2//
   ////////////////////
   row++
   row++
   row++
   runningTittle2 = new Label("Running Option 2: Run For Selected")
   runningTittle2.setStyle("-fx-font-weight: bold")
   pane.add(runningTittle2, 0,row, 2, 1)
   
    
    
    def btnSelected = new Button("Run for Selected")
    btnSelected.setTooltip(new Tooltip("Create the output for the selected objects"))
    btnSelected.setOnAction {e ->
        def hierarchy = qupath.getViewer()?.getHierarchy()
        
        //funciton to decide at the end, basically gets the objects with the selected class
               
        def objects =[]
        
        getSelectedObjects().forEach {
           objects << it 
        }
   
        
        if (objects.isEmpty()) {
            lastParentObjects.clear()
            lastAnnotation = null
            Dialogs.showErrorMessage("Create detection",
                    "Please draw some objects")
            return
        }
        
              
        //Downsample of the img
        def downsampleAT = downsampleDecision(cbAutoDownsample,comboResolutions)
      
        //Post processing selection. function at the end. 
        postProcessingOptions = postProcessingSelection(cbSplit,cbDeleteActual,cbIncludeIgnored,cbSelectNew)
              
        
        for(object in objects) {
            
          
          selectObjects(object)
          classifierPX= classifierCreation(object,  comboChannel.getValue(), downsampleAT, combo.getValue(), floorValueSpinner.getValue(), comboResolutionsClassifier.getValue(),comboPrefilter.getValue(),sigmaSpinner.getValue(), comboAvobe.getValue(), comboBelow.getValue())
          
          actionRunClassifier(object,combo.getValue(),classifierPX[0],classifierPX[1],comboOutput.getValue(),minValueSpinner.getValue(), minHoleSpinner.getValue(), postProcessingOptions.join(','),comboAvobe.getValue(), comboBelow.getValue())
        }
        
        //Delete parents
        
        if(cbDeleteParent.isSelected()== true) {
           removeObjects(objects, true) 
        }
       
    }
    
    
    row++
    pane.add(btnSelected, 0, row, 2, 1)
    
    
   
    
    
    

   
    pane.setHgap(10)
    pane.setVgap(5)
    pane.setPadding(new Insets(5))
     GridPaneUtils.setToExpandGridPaneWidth(combo, btnSelected,comboChannel,comboAvobe,comboBelow,comboParent,comboResolutions,comboResolutionsClassifier,comboOutput,btnRunParents,btnPreview,btnStopPreview)

    def scene = new Scene(pane)
    def stage = new Stage()
    stage.setTitle("Auto threshold GUI")
    stage.setScene(scene)
    stage.initOwner(qupath.getStage())
    return stage
}





//////////////////////////
//AUTO TH FUNCTIONS///////
//////////////////////////


import qupath.lib.images.servers.TransformedServerBuilder
import qupath.lib.roi.interfaces.ROI
import qupath.imagej.tools.IJTools
import qupath.lib.images.PathImage
import qupath.lib.regions.RegionRequest
import ij.ImagePlus
import ij.process.ImageProcessor
import qupath.opencv.ml.pixel.PixelClassifiers
import qupath.lib.images.servers.ColorTransforms.ColorTransform
import qupath.opencv.ops.ImageOp
import qupath.opencv.ops.ImageOps

//This is the original funtion:  @yau-lim (2023) https://gist.github.com/yau-lim/3ed5ae04e82e02939b419afc11dc918c
//the modifications are marked with @I Vieco-Martí:
/* FUNCTIONS */

//This first funcion returns the classifier[0] and the threshold[1]. This is  usefull for the preview, since we just need the classifier. 
def classifierCreation(annotation, channel, thresholdDownsample, thresholdMethod, thresholdFloor, classifierDownsample,prefilterType,prefilterSigma, classAbove,classBelow) {
    def imageData = getCurrentImageData()
    def imageType = imageData.getImageType()
    def server = imageData.getServer()
    def cal = server.getPixelCalibration()
    def classifierChannel

    if (imageType.toString().contains("Brightfield")) {
        def stains = imageData.getColorDeconvolutionStains()
        def colors =getCurrentServer().getMetadata().getChannels()

        if (channel == "Hematoxylin") {
            server = new TransformedServerBuilder(server).deconvolveStains(stains, 1).build()
            classifierChannel = ColorTransforms.createColorDeconvolvedChannel(stains, 1)
        } else if (channel == "Eosin" | channel == "DAB") {
            //@I Vieco-Martí: Simple modification: for H&E the second is Eosin for H-DAB the second is DAB
            server = new TransformedServerBuilder(server).deconvolveStains(stains, 2).build()
            classifierChannel = ColorTransforms.createColorDeconvolvedChannel(stains, 2)
        } else if (channel == "Residual") {
            server = new TransformedServerBuilder(server).deconvolveStains(stains, 3).build()
            classifierChannel = ColorTransforms.createColorDeconvolvedChannel(stains, 3)
        } else if (channel == "Average") {
            server = new TransformedServerBuilder(server).averageChannelProject().build()
            classifierChannel = ColorTransforms.createMeanChannelTransform()
        }else {
            //@I Vieco-Martí: This is for all the other channels
            server = new TransformedServerBuilder(server).extractChannels(channel).build()
            classifierChannel = ColorTransforms.createChannelExtractor(channel)
        }
    } else if (imageType.toString() == "Fluorescence") {
        if (channel == "Average") {
            server = new TransformedServerBuilder(server).averageChannelProject().build()
            classifierChannel = ColorTransforms.createMeanChannelTransform()
        } else {
            server = new TransformedServerBuilder(server).extractChannels(channel).build()
            classifierChannel = ColorTransforms.createChannelExtractor(channel)
        }
    } else {
        logger.error("Current image type not compatible with auto threshold.")
        return
    }


    // Determine threshold value
    logger.info("Calculating threshold value using ${thresholdMethod} method on ${annotation}")
    ROI pathROI = annotation.getROI() // Get QuPath ROI
    PathImage pathImage = IJTools.convertToImagePlus(server, RegionRequest.createInstance(server.getPath(), thresholdDownsample, pathROI)) // Get PathImage within bounding box of annotation
    def ijRoi = IJTools.convertToIJRoi(pathROI, pathImage) // Convert QuPath ROI into ImageJ ROI
    ImagePlus imagePlus = pathImage.getImage() // Convert PathImage into ImagePlus
    // pathImage.getImage().show() // Show image used for histogram
    ImageProcessor ip = imagePlus.getProcessor() // Get ImageProcessor from ImagePlus
    ip.setRoi(ijRoi) // Add ImageJ ROI to the ImageProcessor to limit the histogram to within the ROI only

    // Apply the selected algorithm
    def validThresholds = ["Default", "Huang", "Intermodes", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"]

    if (thresholdMethod in validThresholds){
        ip.setAutoThreshold(thresholdMethod)
    } else {
        logger.error("Invalid auto-threshold method")
        return
    }

    double thresholdValue = ip.getMaxThreshold()
    //@I Vieco-Martí: I changed null to 0
    if (thresholdValue != 0 && thresholdValue < thresholdFloor) {
        thresholdValue = thresholdFloor
    }


    // Define parameters for pixel classifier
    def resolution = cal.createScaledInstance(classifierDownsample, classifierDownsample)
    
    
   //compute radius for some filters and select the filter type
   
   //@I Vieco-Martí: I added all the filters. the structure is the same as here: 
   //https://github.com/qupath/qupath/blob/main/qupath-extension-processing/src/main/java/qupath/process/gui/commands/SimpleThresholdCommand.java
   
   int radius = (int)Math.round(prefilterSigma * 2)
   
   
   if(prefilterType == "Gaussian") {
       prefilter = ImageOps.Filters.gaussianBlur(prefilterSigma)
       
   }else if(prefilterType == "Laplacian") {
       prefilter = ImageOps.Filters.features(Collections.singletonList(MultiscaleFeature.LAPLACIAN), prefilterSigma, prefilterSigma)
       
   }else if(prefilterType == "Erosion") {
       prefilter = ImageOps.Filters.minimum(radius)
       
   }else if(prefilterType == "Dilation") {
        prefilter = ImageOps.Filters.maximum(radius)
       
   }else if(prefilterType == "Opening") {
       prefilter =ImageOps.Filters.opening(radius)
   }else if(prefilterType == "Closing") {
       prefilter =ImageOps.Filters.closing(radius) 
   } else if(prefilterType == "Gradient Magnitude") {
        prefilter =ImageOps.Filters.features(Collections.singletonList(MultiscaleFeature.GRADIENT_MAGNITUDE), prefilterSigma, prefilterSigma)
   }else if(prefilterType == "Weighted_STD") {
       prefilter = ImageOps.Filters.features(Collections.singletonList(MultiscaleFeature.WEIGHTED_STD_DEV), prefilterSigma, prefilterSigma)
       
   }
    //def prefilter = ImageOps.Filters.gaussianBlur(classifierGaussianSigma)

    List<ImageOp> ops = new ArrayList<>()
    ops.add(prefilter)
    ops.add(ImageOps.Threshold.threshold(thresholdValue))

    // Assign classification
    def classificationBelow
    if (classBelow instanceof PathClass) {
        classificationBelow = classBelow
    } else if (classBelow instanceof String) {
        classificationBelow = getPathClass(classBelow)
    } else if (classBelow == null) {
        classificationBelow = classBelow
    }
   
    def classificationAbove
    if (classAbove instanceof PathClass) {
        classificationAbove = classAbove
    } else if (classAbove instanceof String) {
        classificationAbove = getPathClass(classAbove)
    } else if (classAbove == null) {
        classificationAbove = classAbove
    }

    Map<Integer, PathClass> classifications = new LinkedHashMap<>()
    classifications.put(0, classificationBelow)
    classifications.put(1, classificationAbove)

    // Create pixel classifier
    def op = ImageOps.Core.sequential(ops)
    def transformer = ImageOps.buildImageDataOp(classifierChannel).appendOps(op)
    def classifier = PixelClassifiers.createClassifier(
        transformer,
        resolution,
        classifications
    )
    
    return [classifier,thresholdValue] 
    
 }
 
 
 //This funciton creates all the outputs listed
 def actionRunClassifier(annotation,thresholdMethod,classifier,thresholdValue,output,minArea, minHoleArea, classifierObjectOptions,classificationAbove,classificationBelow ) {
     
     // Apply classifier
    //selectObjects(annotation)
    if (output == "Annotation") {
        logger.info("Creating annotations in ${annotation} from ${thresholdMethod}: ${thresholdValue}")
       
        if (classifierObjectOptions) {
            classifierObjectOptions = classifierObjectOptions.split(',')
            def allowedOptions = ["SPLIT", "DELETE_EXISTING", "INCLUDE_IGNORED", "SELECT_NEW"]
            boolean checkValid = classifierObjectOptions.every{allowedOptions.contains(it)}

            if (checkValid) {
                createAnnotationsFromPixelClassifier(classifier, minArea, minHoleArea, classifierObjectOptions)
            } else {
                logger.warn("Invalid create annotation options")
                return
            }
        } else {
            createAnnotationsFromPixelClassifier(classifier, minArea, minHoleArea)
        }
    }
    if (output == "Detection") {
        logger.info("Creating detections in ${annotation} from ${thresholdMethod}: ${thresholdValue}")

        if (classifierObjectOptions) {
            classifierObjectOptions = classifierObjectOptions.split(',')
            def allowedOptions = ["SPLIT", "DELETE_EXISTING", "INCLUDE_IGNORED", "SELECT_NEW"]
            boolean checkValid = classifierObjectOptions.every{allowedOptions.contains(it)}

            if (checkValid) {
                createDetectionsFromPixelClassifier(classifier, minArea, minHoleArea, classifierObjectOptions)
            } else {
                logger.warn("Invalid create detection options")
                return
            }
        } else {
            createDetectionsFromPixelClassifier(classifier, minArea, minHoleArea)
        }
    }
    if (output == "Measurement") {
        logger.info("Measuring thresholded area in ${annotation} from ${thresholdMethod}: ${thresholdValue}")
        def measurementID = "${thresholdMethod} threshold"
        addPixelClassifierMeasurements(classifier, measurementID)
    }
    
   
    if (classificationBelow == null) {
        annotation.measurements.put("${thresholdMethod}: ${classificationAbove.toString()} threshold value", thresholdValue)
    }
    if (classificationAbove == null) {
        annotation.measurements.put("${thresholdMethod}: ${classificationBelow.toString()} threshold value", thresholdValue)
    }
    if (classificationBelow != null && classificationAbove != null) {
        annotation.measurements.put("${thresholdMethod} threshold value", thresholdValue)
    }
    
    
    // If specified output is "threshold value, return threshold value in annotation measurements
    if (output == "Threshold value") {
        logger.info("${thresholdMethod} threshold value: ${thresholdValue}")
        annotation.measurements.put("${thresholdMethod} threshold value", thresholdValue)
        return
    }
    
    
 }
 
 
 
 

//@I Vieco-Martí: this is for the selection of Auto-Downsample or just the user Downsample
def downsampleDecision(cbAutoDownsample,comboResolutions) {
    //Set the downsample according to user decision.
        def downsampleAT = 0
        if(cbAutoDownsample.isSelected()== true) {
           def viewer = getCurrentViewer()
           downsampleAT = viewer.getDownsampleFactor()
       
        }else {
            downsampleAT = comboResolutions.getValue()
        } 
        
        return downsampleAT
}





//@I Vieco-Martí: this is to fit the post-processing into the functionality
def postProcessingSelection(split, delete, include, selectNew) {
   
   def classifierObjectOptions =[]
   
   if(split.isSelected()== true) {
       
       tag1 = "SPLIT"
       classifierObjectOptions <<tag1
       
   }
   
   if(delete.isSelected()== true) {
       
       tag2 = "DELETE_EXISTING"
       classifierObjectOptions <<tag2
       
   }
   
   if(include.isSelected()== true) {
       
       tag3 = "INCLUDE_IGNORED"
       classifierObjectOptions <<tag3
       
       
   }
   
   if(selectNew.isSelected()== true) {
       
       tag4 = "SELECT_NEW"
       classifierObjectOptions << tag4
       
       
   }
   
   return classifierObjectOptions
   
   
   
}

//@I Vieco-Martí: this is to select the parent, when Run for Parents

def decisionParent(parentClassification) {
    def objects =[]
   if(parentClassification == "Unclassified") {
      noClass = getAnnotationObjects().findAll {
         it.getPathClass() == null 
      }
      noClass.forEach {
         objects << it 
      }
     
      
   }else {
      yesClass = getAnnotationObjects().findAll {
         it.getPathClass() == getPathClass(parentClassification) 
      }
      
      yesClass.forEach {
         objects<<it 
      }
          
      
   }
   
   return objects
}


import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay
import qupath.lib.gui.viewer.OverlayOptions
import qupath.lib.gui.viewer.RegionFilter

//This function creates the overlay just into the annotations
def previewSegmentation(classifier) {
     
          
        def quPath = QuPathGUI.getInstance()
        OverlayOptions overlayOption = quPath.getOverlayOptions()
        overlayOption.setPixelClassificationRegionFilter(RegionFilter.StandardRegionFilters.ANY_ANNOTATIONS)
    
        PixelClassificationOverlay previewOverlay = PixelClassificationOverlay.create(overlayOption, classifier)
        previewOverlay.setLivePrediction(true)
        quPath.getViewer().setCustomPixelLayerOverlay(previewOverlay)
    
    
    
     
 }
 
 
 //This function turns off the preview. It is important to set the region filters to Everywhere, to not interfer with other classifier Overlay functions.
 def stopPreviewSegmentation() {
          
       def quPath = QuPathGUI.getInstance()
       quPath.getOverlayOptions().setPixelClassificationRegionFilter(RegionFilter.StandardRegionFilters.EVERYWHERE)
       quPath.getViewer().resetCustomPixelLayerOverlay()
  
     
 }
 

 
 
 
 
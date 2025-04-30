extension = '.jpg'  // 后缀名，png虽然是无损压缩，但是大小将会是jpg的十倍
def imageData = getCurrentImageData()

def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())
def pathOutput = buildFilePath(PROJECT_BASE_DIR, './tiles-10x', name)
mkdirs(pathOutput)

double downsample = 2 // 如果你的原始分辨率是40倍，那么下采样四倍，导出的patch将是10倍，请确认你的原始分辨率
int outputSize = 512 // 输出分辨率，也可以在输入CNN之前resample
int overLap = 0

new TileExporter(imageData)
    .downsample(downsample)
    .imageExtension(extension) // often .tif, .jpg, '.png' or '.ome.tif'
    .tileSize(outputSize) // Define size of each tile, in pixels
    //.annotatedTilesOnly(true) // 如果取消注释, 只会导出具有标注的patch
    //.annotatedCentroidTilesOnly(true)  // 如果是true，只会导出中心包含批注的（而不是只有一点点批注的tile）
    .overlap(overLap)  
    .writeTiles(pathOutput)  

// 重命名，方便后续python处理，运行脚本时不要中断，否则会出现命名错误
def dirOutput = new File(pathOutput)
for (def file in dirOutput.listFiles()) {
    if (!file.isFile() || file.isHidden() || !file.getName().endsWith(extension))
        continue
    def newName = file.getName().replaceAll("=","-").replaceAll("\\[","").replaceAll("\\]","").replaceAll(",","_")
    if (file.getName() == newName)
        continue
    def fileUpdated = new File(file.getParent(), newName)
    println("Renaming ${file.getName()} ---> ${fileUpdated.getName()}")
    file.renameTo(fileUpdated)
}

println('Done!')

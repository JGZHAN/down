# down
* 使用多线程下载网络文件;
* 使用RandomAccessFile在指定文件位置开始写入;
* 使用connection.setRequestProperty("Range", "bytes=" + from + "-" + to)将网络文件分块读取;
# Getting Started

### 依赖MAVEN Lombok

打包成为jar包，
进入到jar包的目录下，直接使用java -jar命令:
* java -jar down.jar 下载目标的地址 [可选-下载到的目标文件夹]

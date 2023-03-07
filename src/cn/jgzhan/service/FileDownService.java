package cn.jgzhan.service;

import static cn.jgzhan.constants.FileConstant.TEMP_FILE_NAME_SUFFIX;
import static cn.jgzhan.thread.ThreadPoll.getThreadPool;
import static cn.jgzhan.utils.FileUtil.getProgressBar;

import cn.jgzhan.bo.BlockInfo;
import cn.jgzhan.bo.DownFileBO;
import cn.jgzhan.thread.BlockFileDownCallable;
import cn.jgzhan.utils.FileUtil;
import com.alibaba.fastjson2.JSON;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jshell.spi.ExecutionControl.StoppedException;

public class FileDownService {

  public static AtomicLong progressSize = new AtomicLong(0L);

  public static double total = 0;

  private final static ThreadPoolExecutor myThreadPool;

  static {
    myThreadPool = getThreadPool();
  }

  public Boolean downLoadFile(DownFileBO downFileBO) throws Exception {

    setTotalAndFileName(downFileBO);

    // 当前文件夹目录下
    File target = new File(downFileBO.getTargetLocalPath() + downFileBO.getFileName());
    var tempFile = new File(target.getAbsoluteFile().getPath() + TEMP_FILE_NAME_SUFFIX);

    // 文件检查
    checkFile(target, tempFile);

    var future = new ArrayList<Future<Boolean>>();

    long before = System.currentTimeMillis();

    var tempExists = tempFile.exists();
    var tempRandomAccessFile = new RandomAccessFile(tempFile, "rw");
    downFileBO = tempExists ? getTempObject(tempRandomAccessFile) : setBlockInfo(downFileBO);

    batchSubmit(downFileBO, target, future);
    //等待下载完成
    var allSuccess = waitDownAndGetResult(future, downFileBO, tempRandomAccessFile);

    if (allSuccess) {
      // 删除临时文件
      tempFile.delete();
    }
    long after = System.currentTimeMillis();
    System.out.println("\n下载完成，花费时间 ：" + (after - before) / 1000 + "秒");
    myThreadPool.shutdown();
    return allSuccess;
  }

  private DownFileBO setBlockInfo(DownFileBO downFileBO) {
    int corePoolSize = myThreadPool.getCorePoolSize();
    var totalSize = downFileBO.getTotalSize();
    int oneBlockSize = totalSize / corePoolSize;

    var blockInfos = new ArrayList<BlockInfo>();
    for (int i = 0; i < corePoolSize; i++) {
      int from = i * oneBlockSize;
      int to = (i + 1) * oneBlockSize > totalSize ? totalSize : (i + 1) * oneBlockSize;
      blockInfos.add(BlockInfo.builder()
          .from(from)
          .to(to)
          .build());
    }
    downFileBO.setBlockInfoList(blockInfos);
    return downFileBO;
  }

  /**
   * 检查文件是否已经下载
   * @param target
   * @param tempFile
   * @throws StoppedException
   */
  private void checkFile(File target, File tempFile) throws StoppedException {
    if (target.exists()) {
      if (!tempFile.exists()) {
        System.out.println(target.getAbsoluteFile().getPath() + "文件已经存在，是否覆盖 ？ y/n ");
        var scanner = new Scanner(System.in);
        var s = scanner.next();
        if (!Objects.equals("y", s)) {
          throw new StoppedException();
        }
        return;
      }
      System.out.println(target.getAbsoluteFile().getPath() + "文件存在，下载未完成，继续下载");
    }
  }

  /**
   * 设置文件大小和文件名
   *
   * @param downFileBO
   * @throws IOException
   */
  private void setTotalAndFileName(DownFileBO downFileBO) throws IOException {

    URL url = new URL(downFileBO.getUrl());
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.connect();
    int contentLength = conn.getContentLength();
    // 文件大小
    downFileBO.setTotalSize(contentLength);
    total = contentLength;

    String fileName = "";
    String contentDisposition = conn.getHeaderField("Content-Disposition");
    if (contentDisposition != null && contentDisposition.indexOf("=") != -1) {
      fileName = contentDisposition.split("=")[1];
    } else {
      fileName = url.getPath().substring(url.getPath().lastIndexOf("/") + 1);
    }
    // 文件名
    downFileBO.setFileName(fileName);
    // 设置目标文件地址
    var targetFolder = FileUtil.getTargetFolder(downFileBO.getTargetLocalPath());
    downFileBO.setTargetLocalPath(targetFolder);

    conn.disconnect();
  }


  /**
   * 等待下载完，并获取下载结果
   *
   * @param future
   * @param downFileBO
   * @param tempRandomAccessFile
   * @return 是否全部片段下载成功
   */
  private boolean waitDownAndGetResult(List<Future<Boolean>> future, DownFileBO downFileBO,
      RandomAccessFile tempRandomAccessFile) throws IOException, InterruptedException {
    while (true) {
      var finish = future.stream().allMatch(Future::isDone);
      if (finish) {
        break;
      }
      saveTempFile(tempRandomAccessFile, downFileBO);
      printLog();
      Thread.sleep(500 * 1);
    }
    return future.stream().allMatch(futureItem -> {
      try {
        return futureItem.get();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private void saveTempFile(RandomAccessFile tempRandomAccessFile, DownFileBO downFileBO)
      throws IOException {
    // TODO: 2023/3/7 存在风险，若清空后没有写入成功，则下次进来时获取对象失败
    tempRandomAccessFile.setLength(0);
    tempRandomAccessFile.writeBytes(JSON.toJSONString(downFileBO));

  }

  /**
   * 打印进度
   */
  private void printLog() {
    var processVla = progressSize.get() * 100 / total;
    var progressBar = getProgressBar(processVla);
    System.out.print("\r" + "下载进度 ：" + String.format("%.2f", processVla) + "%" + progressBar);
  }


  private void batchSubmit(DownFileBO downFileBO, File target, ArrayList<Future<Boolean>> future) {

    AtomicInteger remainBlockSize = new AtomicInteger();
    downFileBO.getBlockInfoList().forEach(e -> {
      future.add(submit(downFileBO.getUrl(), target, e));
      remainBlockSize.addAndGet((e.getTo() - e.getFrom()));
    });
    progressSize.addAndGet(downFileBO.getTotalSize() - remainBlockSize.get());
  }

  private DownFileBO getTempObject(RandomAccessFile tempRandomAccessFile) throws IOException {
    var tempBuffer = new byte[1024 * 2];
    StringBuilder tempSb = new StringBuilder();
    while (true) {
      var read = tempRandomAccessFile.read(tempBuffer);
      if (read == -1) {
        break;
      }
      var tempTempBuffer = Arrays.copyOfRange(tempBuffer, 0, read);
      tempSb.append(new String(tempTempBuffer));
    }
    return JSON.parseObject(tempSb.toString(), DownFileBO.class);
  }

  private Future<Boolean> submit(String filePath, File target, BlockInfo blockInfo) {

    var submit = myThreadPool.submit(new BlockFileDownCallable(blockInfo, target, filePath));
    return submit;
  }

}

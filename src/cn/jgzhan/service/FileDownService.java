package cn.jgzhan.service;

import static cn.jgzhan.thread.ThreadPoll.getThreadPool;
import static cn.jgzhan.utils.FileUtil.getProgressBar;

import cn.jgzhan.bo.DownFileBO;
import cn.jgzhan.thread.BlockFileDownCallable;
import cn.jgzhan.utils.FileUtil;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

public class FileDownService {

  public static AtomicLong progressSize = new AtomicLong(0L);

  public static double total = 0;

  private final static ThreadPoolExecutor myThreadPool;

  static {
    myThreadPool = getThreadPool();
  }

  public Boolean downLoadFile(DownFileBO downFileBO) {

    long before = System.currentTimeMillis();
    int totalSize;
    String fileName;
    try {
      var url = new URL(downFileBO.getUrl());
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.connect();
      int contentLength = connection.getContentLength();
      totalSize = contentLength;
      total = contentLength;
      fileName = FileUtil.getName(url.getPath());
      if (totalSize == 0) {
        return true;
      }
    } catch (IOException e) {
      return false;
    }

    // 当前文件夹目录下
    var targetFolder = FileUtil.getTargetFolder(downFileBO.getTargetLocalPath());
    File target = new File(targetFolder + fileName);
    int corePoolSize = Runtime.getRuntime().availableProcessors();
    int oneBlockSize = totalSize / corePoolSize;
    List<Future<Boolean>> future = new ArrayList<>(corePoolSize);

    for (int i = 0; i < corePoolSize; i++) {
      future.add(execute(downFileBO.getUrl(), target, oneBlockSize, i, totalSize));
    }
    //等待下载完成
    var allSuccess = waitDownAndGetResult(future);

    long after = System.currentTimeMillis();
    System.out.println("\n下载完成，花费时间 ：" + (after - before) / 1000 + "秒");
    myThreadPool.shutdown();
    return allSuccess;
  }

  /**
   * 等待下载完，并获取下载结果
   * @param future
   * @return 是否全部片段下载成功
   */
  private boolean waitDownAndGetResult(List<Future<Boolean>> future) {
    while (true) {
      var finish = future.stream().allMatch(Future::isDone);
      if (finish) {
        break;
      }
      printLog();
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

  /**
   * 打印进度
   */
  private static void printLog() {
    try {
      var processVla = progressSize.get() * 100 / total;
      var progressBar = getProgressBar(processVla);
      System.out.print("\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b"
          + "\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b"
          + "\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b"
          + "\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b" + "下载进度 ：" + String.format(
          "%.2f", processVla) + "%" + progressBar);
      Thread.sleep(500 * 1);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private Future<Boolean> execute(String filePath, File target, int oneBlockSize, int i,
      int totalSize) {
    int to = (i + 1) * oneBlockSize > totalSize ? totalSize : (i + 1) * oneBlockSize;

    Future<Boolean> submit = myThreadPool.submit(
        new BlockFileDownCallable(i * oneBlockSize, to, target, filePath, (i + 1)));
    return submit;
  }

}

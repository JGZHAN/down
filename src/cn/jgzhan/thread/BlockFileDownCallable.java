package cn.jgzhan.thread;

import cn.jgzhan.service.FileDownService;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

/**
 * @author ZHAN jgZHAN
 * @create 2022-07-30 18:10
 */
//@Slf4j
public class BlockFileDownCallable implements Callable {

  private int from;
  private int to;
  private File target;
  private String uri;
  private int blockNum;

  public BlockFileDownCallable(int from, int to, File target, String uri, int blockNum) {
    this.from = from;
    this.to = to;
    this.target = target;
    this.uri = uri;
    this.blockNum = blockNum;
  }

  public int getFrom() {
    return from;
  }

  public int getTo() {
    return to;
  }


  @Override
  public Object call() throws Exception {
    //download and save data
    try {

      InputStream inputStream = getInputStream();
      BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
      RandomAccessFile randomAccessFile = getRandomAccessFile();
      byte[] buffer = new byte[1024 * 10];
      int readCount;

      while (true) {
        readCount = bufferedInputStream.read(buffer, 0, buffer.length);
        if (readCount < 0) {
          break;
        }
        randomAccessFile.write(buffer, 0, readCount);
        FileDownService.progressSize.addAndGet(readCount);
      }

      randomAccessFile.close();
      inputStream.close();
      bufferedInputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }


  private RandomAccessFile getRandomAccessFile() throws IOException {
    RandomAccessFile randomAccessFile = new RandomAccessFile(target, "rw");
    randomAccessFile.seek(from);
    return randomAccessFile;
  }

  private InputStream getInputStream() throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(uri).openConnection();
    connection.setRequestProperty("Range", "bytes=" + from + "-" + to);
    connection.setRequestProperty("User-Agent",
        "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
    connection.connect();
    InputStream inputStream = connection.getInputStream();
    return inputStream;
  }
}
package cn.jgzhan.utils;


import static cn.jgzhan.constants.FileConstant.DEFAULT_FOLDER;

import cn.jgzhan.constants.FileConstant;

public class FileUtil {

  public static String getName(String fileName) {
    if (fileName == null) {
      return null;
    } else {
      checkFileName(fileName);
      int index = indexOfLastSeparator(fileName);
      return fileName.substring(index + 1);
    }
  }

  private static void checkFileName(String fileName) {
    var trim = fileName.trim();
    if (trim == "") {
      throw new RuntimeException("路径不可为空");
    }
  }

  public static int indexOfLastSeparator(String fileName) {
    if (fileName == null) {
      return -1;
    } else {
      int lastUnixPos = fileName.lastIndexOf(47);
      int lastWindowsPos = fileName.lastIndexOf(92);
      return Math.max(lastUnixPos, lastWindowsPos);
    }
  }



  /**
   * 优先获取参数的地址，否则默认当前执行环境的文件夹
   * @param parFolder
   * @return
   */
  public static String getTargetFolder(String parFolder) {
    String targetLocalPath;
    if (parFolder == null || (targetLocalPath = parFolder.trim()) == "") {
      return DEFAULT_FOLDER;
    }
    if (!targetLocalPath.endsWith(FileConstant.FOLDER_SUFFIX)) {
      targetLocalPath += FileConstant.FOLDER_SUFFIX;
    }
    return targetLocalPath;
  }


  public static String getProgressBar(double processVla) {
    // 满格50格
    var processVlaInt = (int) processVla / 2;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 50; i++) {
      if (i < processVlaInt) {
        sb.append("◼");
      } else {
        sb.append("◻");
      }
    }
    return sb.toString();
  }
}

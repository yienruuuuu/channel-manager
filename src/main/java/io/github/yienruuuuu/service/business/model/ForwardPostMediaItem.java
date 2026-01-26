package io.github.yienruuuuu.service.business.model;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
public class ForwardPostMediaItem {
    private final String mediaType;
    private final String fileId;

    /**
     * 建立媒體項目。
     *
     * @param mediaType 媒體類型
     * @param fileId 檔案 ID
     */
    public ForwardPostMediaItem(String mediaType, String fileId) {
        this.mediaType = mediaType;
        this.fileId = fileId;
    }

    /**
     * 取得媒體類型。
     *
     * @return 媒體類型
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * 取得檔案 ID。
     *
     * @return 檔案 ID
     */
    public String getFileId() {
        return fileId;
    }
}

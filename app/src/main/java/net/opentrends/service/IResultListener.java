/**
 * Copyright (c) 2016 Mattermost, Inc. All Rights Reserved.
 * See License.txt for license information.
 */
package net.opentrends.service;

public interface IResultListener<T> {
    void onResult(Promise<T> promise);
}

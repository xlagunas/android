/**
 * Copyright (c) 2016 Mattermost, Inc. All Rights Reserved.
 * See License.txt for license information.
 */
package net.opentrends.service.jacksonconverter;

import com.fasterxml.jackson.databind.type.TypeFactory;
import net.opentrends.service.Promise;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import retrofit.Call;
import retrofit.CallAdapter;
import retrofit.Retrofit;

public class PromiseConverterFactory implements CallAdapter.Factory {

    public static PromiseConverterFactory create() {
        return new PromiseConverterFactory();
    }

    @Override
    public CallAdapter<?> get(final Type returnType, Annotation[] annotations, Retrofit retrofit) {

        Class<?> cls = TypeFactory.rawClass(returnType);

        if (!Promise.class.isAssignableFrom(cls))
            return null;

        if (!(returnType instanceof ParameterizedType)) {
            throw new IllegalStateException(
                    "Promise must have generic type (e.g., Promise<T>)");
        }

        final Type responseType = ((ParameterizedType) returnType).getActualTypeArguments()[0];

        return new CallAdapter<Promise<?>>() {
            @Override
            public Type responseType() {
                return responseType;
            }

            @Override
            public <R> Promise<R> adapt(Call<R> call) {
                Promise<R> promise = new Promise<R>();
                call.enqueue(promise.callback());

                promise.onStarted();
                return promise;
            }
        };
    }

}

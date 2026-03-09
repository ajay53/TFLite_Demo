package com.example.tflitedemo.di

import com.example.tflitedemo.data.ObjectDetectorImpl
import com.example.tflitedemo.domain.ObjectDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DetectorModule {

    @Binds
    @Singleton
    abstract fun bindObjectDetector(
        objectDetectorImpl: ObjectDetectorImpl
    ): ObjectDetector
}
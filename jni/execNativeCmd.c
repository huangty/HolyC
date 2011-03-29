#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <android/log.h>
//#include <cutils/properties.h>
#include <sys/system_properties.h>
//#include "execNativeCmd.h"

#define LOG_TAG "HOLYC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)


/*JNIEXPORT jstring JNICALL Java_edu_stanford_holyc_jni_NativeCallWrapper_getProp
  (JNIEnv *env, jclass class, jstring name)
{
  const char *nameString;
  nameString = (*env)->GetStringUTFChars(env, name, 0);

  char value[PROPERTY_VALUE_MAX];
  char *default_value;
  jstring jstrOutput;
  
  default_value = "undefined";
  property_get(nameString, value, default_value);

  jstrOutput = (*env)->NewStringUTF(env, value);

  (*env)->ReleaseStringUTFChars(env, name, nameString);  

  return jstrOutput;
}*/

JNIEXPORT jint JNICALL Java_edu_stanford_holyc_jni_NativeCallWrapper_runCommand
  (JNIEnv *env, jclass class, jstring command)
{
  const char *commandString;
  commandString = (*env)->GetStringUTFChars(env, command, 0);
  int exitcode = system(commandString); 
  LOGI("native call executing: %s\n", commandString);
  (*env)->ReleaseStringUTFChars(env, command, commandString);  
  return (jint)exitcode;
}


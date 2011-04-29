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

#define PROPERTY_KEY_MAX   32
#define PROPERTY_VALUE_MAX  92
#define MAX_RESULT_LEN 512 
#define LINE_LEN 128


JNIEXPORT jstring JNICALL Java_net_holyc_jni_NativeCallWrapper_getProp
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
}

JNIEXPORT jint JNICALL Java_net_holyc_jni_NativeCallWrapper_runCommand
  (JNIEnv *env, jclass class, jstring command)
{
  const char *commandString;
  commandString = (*env)->GetStringUTFChars(env, command, 0);
  int exitcode = system(commandString); 
  //LOGI("native call executing: %s\n", commandString);
  (*env)->ReleaseStringUTFChars(env, command, commandString);  
  return (jint)exitcode;
}

JNIEXPORT jstring JNICALL Java_net_holyc_jni_NativeCallWrapper_getResultByCommand
  (JNIEnv *env, jclass class, jstring command)
{
  const char *commandString;
  char result[MAX_RESULT_LEN];
  char result2[MAX_RESULT_LEN];
  char line[LINE_LEN];
  jstring jstrResult = NULL;

  commandString = (*env)->GetStringUTFChars(env, command, 0);
  FILE* pin = popen(commandString, "r");
  if (!pin) return jstrResult;
  //sleep(2);
  LOGI("trying to get result\n");
  fgets(result, MAX_RESULT_LEN, pin);
  LOGI("get result!\n");
  //clear the result from pin
  //while(fgets(line, LINE_LEN, pin)){}
  pclose(pin);
  memset(result2, '\0', MAX_RESULT_LEN);
  strcpy(result2, result);
  jstrResult = (*env)->NewStringUTF(env, result2);
  (*env)->ReleaseStringUTFChars(env, command, commandString);
  return jstrResult;
}


int property_get(const char *key, char *value, const char *default_value)
{
    int len;

    len = __system_property_get(key, value);
    if(len > 0) {
        return len;
    }

    if(default_value) {
        len = strlen(default_value);
        memcpy(value, default_value, len + 1);
    }
    return len;
}


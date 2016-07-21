#!/bin/sh
#****************************************************
#AUTHOR:MANOJ V V
#DESCRIPTION:Script for lp-definition-update.
#DATE:25-05-16
#****************************************************

#*****************************************************
cd docs/language_model/definitions/iso                    #move inside th folder

for file1 in *
do
   json_content1=`cat $file1`
   curl -i -X POST -H "Content-Type: application/json" -H "user-id: ansible_user" -d "$json_content1" http://{{ service_url }}/language-service/v1/language/language/importDefinition
done


#*****************************************************
cd docs/language_model/definitions/language                #move inside th folder

for file1 in *
do
   json_content1=`cat $file1`
   curl -i -X POST -H "Content-Type: application/json" -H "user-id: ansible_user" -d "$json_content1" http://{{ service_url }}/language-service/v1/language/$1/importDefinition
done


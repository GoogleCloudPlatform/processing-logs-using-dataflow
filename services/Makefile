# Copyright Google Inc. 2015
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

.PHONY: build image tag push

services = home browse locate
project = cloud-solutions-images

build:
	$(foreach s, $(services), \
		GOOS=linux go build -o $(s)/$(s)-service $(s)/$(s)-service.go ;)	

image:
	$(foreach s, $(services), \
		docker build -t crcsmnky/sample-$(s)-service $(s) ;)

tag:
	$(foreach s, $(services), \
		docker tag -f crcsmnky/sample-$(s)-service gcr.io/$(project)/sample-$(s)-service ;)

push:
	$(foreach s, $(services), \
		gcloud docker --project $(project) push gcr.io/$(project)/sample-$(s)-service ;)

clean:
	$(foreach s, $(services), \
		rm $(s)/$(s)-service ;)
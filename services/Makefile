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


SERVICES = home browse locate
GCR_PROJECT = cloud-solutions-images
APPLICATION = processing-logs-at-scale

all:  image tag push

.PHONY: all

build:
	$(foreach s, $(SERVICES), \
		GOOS=linux go build -o $(s)/$(s)-service $(s)/$(s)-service.go ;)	

image:
	$(foreach s, $(SERVICES), \
		docker build -t $(APPLICATION)-$(s)-service $(s) ;)

tag:
	$(foreach s, $(SERVICES), \
		docker tag $(APPLICATION)-$(s)-service gcr.io/$(GCR_PROJECT)/$(APPLICATION)-$(s)-service ;)

push:
	$(foreach s, $(SERVICES), \
		docker push gcr.io/$(GCR_PROJECT)/$(APPLICATION)-$(s)-service ;)

clean:
	$(foreach s, $(SERVICES), \
		rm $(s)/$(s)-service ;)

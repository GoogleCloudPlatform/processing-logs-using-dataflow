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
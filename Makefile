.PHONY: start-minikube
start-minikube:
	bash hack/start-minikube.sh

.PHONY: install-trustify
install-trustify:
	bash hack/install-trustify.sh

.PHONY: install-trustify-bundle
install-trustify-bundle:
	bash hack/install-trustify-bundle.sh

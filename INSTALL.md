## Install operator
 
```bash
kubectx minikube
kubectl apply -f deploy.yaml
```

## Install CRD

```bash
kubectx minikube
kubectl apply -f src/main/resources/predefinedendpointcatalog-crd.yaml
kubectl apply -f src/main/resources/requiredendpointset-crd.yaml
```

## Install example-target-system

```bash
kubectx minikube
kubectl apply -f example-predefinedendpointcatalog.yaml
```

```bash
kubectx minikube
kubectl delete -f example-predefinedendpointcatalog.yaml
```

```bash
kubectx minikube
kubectl delete -f deploy.yaml
```

## Install Cilium

* https://chatgpt.com/c/69c6ecb2-ba3c-8394-8cc9-2973e63e7e53

```bash
brew install cilium-cli
```

```bash
kubectx minikube

cilium install #--version 1.19.2

cilium status --wait
```

```bash
kubectx minikube

cilium hubble enable --ui

cilium status --wait
```

```bash
cilium hubble ui
```

```bash
cilium hubble port-forward&

hubble status

hubble observe
```

## Test Server (Nginx)

```bash
kubectx minikube
kubectl apply -n default -f nginx.yaml
```
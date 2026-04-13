## Install operator

```bash
kubectx minikube
kubectl apply -f deploy.yaml
```

## Install CRD

```bash
kubectl apply -f src/main/resources/crd.yaml
```

## Install example-target-system

```bash
kubectl apply -f example-target-system.yaml
```

```bash
kubectl delete -f example-target-system.yaml
```

```bash
kubectx minikube
kubectl delete -f deploy.yaml
```


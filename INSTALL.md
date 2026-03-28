## Install operator

```bash
kubectx minikube
kubectl apply -f deploy.yaml
```

## Install CRD

```bash
kubectl apply -f src/main/resources/crd.yaml
```

## Install example group

```bash
kubectl apply -f example-group.yaml
```

```bash
kubectl delete -f example-group.yaml
```

```bash
kubectx minikube
kubectl delete -f deploy.yaml
```


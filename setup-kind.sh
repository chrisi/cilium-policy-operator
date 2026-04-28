#!/bin/bash
set -e  # Exit on error

CLUSTER_NAME="cilium-test"

echo "Creating Kind cluster..."
kind create cluster --name "$CLUSTER_NAME" --config kind-config.yaml

echo "Installing Cilium..."
cilium install

echo "Waiting for Cilium to stabilize..."
cilium status --wait

echo "Confirming nodes are Ready..."
kubectl wait --for=condition=Ready nodes --all --timeout=300s

echo "Enable Hubble..."
cilium hubble enable --ui

echo "Installing Tetragon..."
helm install tetragon cilium/tetragon \
  --namespace kube-system \
  --set tetragon.hostProcPath=/proc

#kind load docker-image docker.io/library/cilium-policy-operator:0.0.1-SNAPSHOT
#kubectl apply -f deploy.yaml

kubectl apply -f src/main/resources/predefinedendpointcatalog-crd.yaml
kubectl apply -f src/main/resources/requiredendpointset-crd.yaml

kubectl apply -f example-predefinedendpointcatalog.yaml
kubectl apply -f example-requiredendpointset.yaml

echo "Setup complete!"

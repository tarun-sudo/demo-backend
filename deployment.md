# Manual Deployment: Docker -> ACR -> AKS

Run these commands from PowerShell.

## 1) Go to project

```powershell
cd "C:\Users\hamik\Downloads\demo (1)\demo"
```

## 2) Azure login and subscription

```powershell
az login --use-device-code
az account set --subscription "Azure subscription 1"
```

## 3) Build ARM64 image and push to ACR

```powershell
az acr login --name etbArtifactory
docker buildx create --name aksbuilder --use
docker buildx build --platform linux/arm64 -t etbartifactory.azurecr.io/demo-backend:upload-v1 --push .
```

## 4) Connect kubectl to AKS

```powershell
az aks get-credentials --resource-group demoResourceGroup --name DevCluster --overwrite-existing
az aks update --resource-group demoResourceGroup --name DevCluster --attach-acr etbArtifactory
```

## 5) Create/update app secret (H2 demo mode)

```powershell
kubectl create secret generic demo-backend-db `
  --from-literal=DB_URL='jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' `
  --from-literal=DB_USERNAME='sa' `
  --from-literal=DB_PASSWORD='' `
  --dry-run=client -o yaml | kubectl apply -f -
```

## 6) Apply manifests and roll out image

```powershell
kubectl apply -f .\k8s\service.yaml
kubectl apply -f .\k8s\deployment.yaml
kubectl set image deployment/demo-backend demo-backend=etbartifactory.azurecr.io/demo-backend:upload-v1
kubectl rollout status deployment/demo-backend --timeout=300s
kubectl get pods -l app=demo-backend -o wide
kubectl get svc demo-backend
```

## 7) Access APIs from Postman (ClusterIP service)

```powershell
kubectl port-forward svc/demo-backend 8080:80
```

Then use:

- `POST http://localhost:8080/upload` (form-data file)
- `GET http://localhost:8080/tables`
- `GET http://localhost:8080/table-content?file=large-dataset.csv`

# Gpu-affinity-in-nodemanger
When gpu allocation base on yarn,
like tensorflow on yarn..., we want container`s gpu
cores all are on a same cpu, this is gpu affinity.
![image](https://github.com/hadooptofly/pictures/blob/master/PastedGraphic-1.png)
in picture above, which two core tuple result is "SOC" mean they are not on a 
same cpu, others mean they are on same cpu.
```xml
this repo is do this gpu management:
Example:
    8 gpu cores: 1 is sigle, 2 is on cpu0, 5 is on cpu1
    a container need 2 gpu: got 2 from cpu0
    a container need 3 gpu: got 3 from cpu1
    a container need 1 gpu: got that sigle one.
  
 when got the gpu assigned from mamanger above,
 set this to a env in launch context of container,
 and when container get launched it will bind the 
 corresponding gpu.
 
